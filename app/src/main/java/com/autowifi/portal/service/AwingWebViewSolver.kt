package com.autowifi.portal.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.autowifi.portal.data.WifiTargetConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class AwingWebViewSolver(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Solves the AWING captive portal using a background headless WebView.
     * Injects the JavaScript auto-solver, handles redirects, bypasses ads/timers,
     * and verifies HTTP 204 connectivity upon completion.
     */
    suspend fun solve(network: Network? = null, initialPortalUrl: String? = null): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        // Step 0: Check if internet is already available (HTTP 204)
        if (verifyInternetAccess(network)) {
            Log.i(TAG, "Internet already verified (HTTP 204). No captive portal login required.")
            return true
        }

        // Bind current process to target Wi-Fi network so all WebView and socket traffic routes through it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && network != null) {
            try {
                connectivityManager?.bindProcessToNetwork(network)
                Log.d(TAG, "Bound process to network: $network")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to bind process to network", e)
            }
        }

        var webView: WebView? = null
        var periodicVerificationJob: Job? = null
        val deferredResult = CompletableDeferred<Boolean>()

        return try {
            withTimeoutOrNull(SOLVER_TIMEOUT_MS) {
                // Detect redirect or landing URL
                val targetUrl = initialPortalUrl ?: detectCaptivePortalUrl(network)
                Log.i(TAG, "Starting AWING automated solver on URL: $targetUrl")

                withContext(Dispatchers.Main) {
                    webView = WebView(context.applicationContext).apply {
                        layout(0, 0, 1080, 1920)
                        onResume()
                        resumeTimers()

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            useWideViewPort = true
                            loadWithOverviewMode = true

                            // Strip '; wv' to prevent AWING detecting Android WebView and halting on AndroidWaiting screen
                            val originalUa = userAgentString ?: ""
                            userAgentString = if (originalUa.contains("; wv")) {
                                originalUa.replace("; wv", "")
                            } else if (!originalUa.contains("Chrome/")) {
                                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            } else {
                                originalUa
                            }
                        }

                        // Bridge interface for JS callbacks
                        addJavascriptInterface(
                            AwingBridge(
                                onSolverSuccess = { detail ->
                                    Log.i(TAG, "[AwingBridge] Portal reported success: $detail")
                                    // Trigger quick verification loop
                                    CoroutineScope(Dispatchers.IO).launch {
                                        for (i in 1..4) {
                                            delay(1000)
                                            if (verifyInternetAccess(network)) {
                                                deferredResult.complete(true)
                                                return@launch
                                            }
                                        }
                                        Log.w(TAG, "Portal reported success but HTTP 204 was not verified yet.")
                                    }
                                },
                                onLog = { msg ->
                                    Log.d(TAG, "[AwingBridge JS] $msg")
                                }
                            ),
                            "AwingBridge"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d(TAG, "[JS Console] ${consoleMessage?.message()}")
                                return true
                            }

                            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                result?.confirm()
                                return true
                            }

                            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                result?.confirm()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                Log.d(TAG, "WebView navigating to: $url")
                                return false // Let WebView load redirects internally
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d(TAG, "WebView onPageFinished: $url")

                                // Check if page is already in success / welcome state
                                val lower = url?.lowercase() ?: ""
                                if (lower.contains("/success") || lower.contains("/state/connected") || lower.contains("/welcome")) {
                                    Log.i(TAG, "Success URL detected in navigation: $url")
                                    CoroutineScope(Dispatchers.IO).launch {
                                        delay(800)
                                        if (verifyInternetAccess(network)) {
                                            deferredResult.complete(true)
                                        }
                                    }
                                }

                                // Inject JavaScript Auto-Solver
                                evaluateJavascript(AWING_SOLVER_SCRIPT) { res ->
                                    Log.d(TAG, "JS Solver injected. Result: $res")
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                Log.w(TAG, "Captive portal SSL error: $error. Proceeding to allow gateway authentication.")
                                handler?.proceed()
                            }
                        }

                        // Load initial portal or probe redirect
                        loadUrl(targetUrl)
                    }

                    // Background watchdog checking internet availability while WebView is solving
                    periodicVerificationJob = CoroutineScope(Dispatchers.IO).launch {
                        delay(2000)
                        while (isActive && !deferredResult.isCompleted) {
                            if (verifyInternetAccess(network)) {
                                Log.i(TAG, "Periodic check: Internet access verified (HTTP 204)!")
                                deferredResult.complete(true)
                                break
                            }
                            delay(2000)
                        }
                    }
                }

                // Suspend until solver succeeds or timeout
                deferredResult.await()
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error during AWING WebView solver execution", e)
            false
        } finally {
            periodicVerificationJob?.cancel()

            withContext(NonCancellable) {
                withContext(Dispatchers.Main) {
                    try {
                        webView?.apply {
                            stopLoading()
                            removeJavascriptInterface("AwingBridge")
                            clearHistory()
                            loadUrl("about:blank")
                            destroy()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error tearing down WebView", e)
                    }
                    webView = null
                }

                // Unbind process from network
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && network != null) {
                    try {
                        connectivityManager?.bindProcessToNetwork(null)
                        Log.d(TAG, "Unbound process from network")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Probes the network with an HTTP 204 check to detect redirect URL if behind a captive portal.
     */
    suspend fun detectCaptivePortalUrl(network: Network?): String = withContext(Dispatchers.IO) {
        val testUrl = WifiTargetConfig.PROBE_URL
        try {
            if (network != null) {
                val conn = network.openConnection(URL(testUrl)) as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.connect()
                val responseCode = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                Log.d(TAG, "Captive probe response code: $responseCode, Location: $location")

                if (!location.isNullOrBlank()) {
                    return@withContext location
                }
            } else {
                val request = Request.Builder().url(testUrl).build()
                val response = httpClient.newCall(request).execute()
                val location = response.header("Location")
                response.close()
                if (!location.isNullOrBlank()) {
                    return@withContext location
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error probing captive portal redirect", e)
        }

        // Default trigger URL if no redirect found
        return@withContext WifiTargetConfig.AWING_TRIGGER_URL
    }

    /**
     * Verifies whether internet access is actively available by probing generate_204.
     */
    suspend fun verifyInternetAccess(network: Network?): Boolean = withContext(Dispatchers.IO) {
        val probeUrls = listOf(
            WifiTargetConfig.PROBE_URL,
            WifiTargetConfig.FALLBACK_PROBE_URL
        )

        for (probeUrl in probeUrls) {
            try {
                if (network != null) {
                    val conn = network.openConnection(URL(probeUrl)) as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.requestMethod = "GET"
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 204) {
                        return@withContext true
                    }
                } else {
                    val request = Request.Builder().url(probeUrl).build()
                    val response = httpClient.newCall(request).execute()
                    val code = response.code
                    response.close()
                    if (code == 204) {
                        return@withContext true
                    }
                }
            } catch (_: Exception) {
                // Ignore and try fallback
            }
        }
        false
    }

    private class AwingBridge(
        private val onSolverSuccess: (String) -> Unit,
        private val onLog: (String) -> Unit
    ) {
        @JavascriptInterface
        fun onSuccess(detail: String) {
            onSolverSuccess(detail)
        }

        @JavascriptInterface
        fun log(msg: String) {
            onLog(msg)
        }
    }

    companion object {
        private const val TAG = "AwingWebViewSolver"
        private const val SOLVER_TIMEOUT_MS = 38_000L

        val AWING_SOLVER_SCRIPT = """
(function () {
    'use strict';

    if (window.__AWING_SOLVER_ACTIVE__) {
        console.log('[AWING_SOLVER] Already running.');
        return;
    }
    window.__AWING_SOLVER_ACTIVE__ = true;

    console.log('[AWING_SOLVER] Starting AWING Portal Auto-Solver...');

    function log(msg) {
        console.log('[AWING_SOLVER] ' + msg);
        try {
            if (window.AwingBridge && typeof window.AwingBridge.log === 'function') {
                window.AwingBridge.log(msg);
            }
        } catch (e) {}
    }

    function notifySuccess(detail) {
        log('Solved portal successfully: ' + detail);
        try {
            if (window.AwingBridge && typeof window.AwingBridge.onSuccess === 'function') {
                window.AwingBridge.onSuccess(detail);
            }
        } catch (e) {}
    }

    // Step 1: Auto-accept terms / agreements checkboxes
    function checkAgreements() {
        const checkboxes = document.querySelectorAll(
            'input[type="checkbox"]:not(:checked), #awing-captive__agreement input[type="checkbox"]'
        );
        checkboxes.forEach(function(cb) {
            try {
                cb.checked = true;
                cb.dispatchEvent(new Event('change', { bubbles: true }));
                cb.dispatchEvent(new Event('click', { bubbles: true }));
                log('Checked terms checkbox: ' + (cb.id || cb.name || 'unnamed'));
            } catch (e) {}
        });
    }

    // Step 2: Skip customer information survey popups if presented
    function handleCustomerPopup() {
        if (window.awingStateMachineContextActions) {
            try {
                if (typeof window.awingStateMachineContextActions.customerFilterFormRemindLater === 'function') {
                    window.awingStateMachineContextActions.customerFilterFormRemindLater();
                    log('Invoked customerFilterFormRemindLater()');
                }
            } catch (e) {}
        }
        const skipButtons = document.querySelectorAll(
            '#awing-captive__customerscreen button, .customer-skip-btn, button[class*="skip"], a[class*="skip"]'
        );
        skipButtons.forEach(function(btn) {
            try {
                btn.click();
                log('Clicked customer popup skip button');
            } catch (e) {}
        });
    }

    // Step 3: Fast-forward countdowns and unlock disabled controls
    function unlockCountdowns() {
        const disabledButtons = document.querySelectorAll('button[disabled], input[type="submit"][disabled], .btn-disabled');
        disabledButtons.forEach(function(btn) {
            try {
                btn.removeAttribute('disabled');
                btn.classList.remove('btn-disabled');
                btn.classList.add('btn-active');
            } catch (e) {}
        });

        const videos = document.querySelectorAll('video');
        videos.forEach(function(v) {
            try {
                if (!v.paused && v.duration > 0 && v.currentTime < v.duration) {
                    v.currentTime = v.duration;
                    v.dispatchEvent(new Event('ended', { bubbles: true }));
                    log('Fast-forwarded ad video to end');
                }
            } catch (e) {}
        });

        if (window.awingStateMachineContextActions && window.awingConstants) {
            try {
                const events = window.awingConstants.EVENT_MESSAGE;
                if (events) {
                    if (events.LOADING_ANIMATION_LOGIN_COMPLETE) {
                        window.awingStateMachineContextActions.completeEvent(events.LOADING_ANIMATION_LOGIN_COMPLETE);
                    }
                    if (events.GET_CAMPAIGN_COMPLETE) {
                        window.awingStateMachineContextActions.completeEvent(events.GET_CAMPAIGN_COMPLETE);
                    }
                }
            } catch (e) {}
        }
    }

    // Step 4: Interact with AWING State Machine directly
    function triggerStateMachineBypass() {
        if (window.awingStateMachineContextActions) {
            try {
                if (typeof window.awingStateMachineContextActions.completeAd === 'function') {
                    window.awingStateMachineContextActions.completeAd();
                    log('Triggered awingStateMachineContextActions.completeAd()');
                    return true;
                }
            } catch (e) {}

            try {
                if (typeof window.awingStateMachineContextActions.nextView === 'function') {
                    window.awingStateMachineContextActions.nextView();
                    log('Triggered awingStateMachineContextActions.nextView()');
                    return true;
                }
            } catch (e) {}
        }

        if (window.awingStateMachine && typeof window.awingStateMachine.transition === 'function') {
            try {
                const currentState = window.awingStateMachine.getState ? window.awingStateMachine.getState() : '';
                if (currentState === 'DisplayingLoginPage') {
                    window.awingStateMachine.transition('Submit');
                    log('Transitioned StateMachine to Submit');
                    return true;
                } else if (currentState === 'DisplayingCustomerPopup') {
                    window.awingStateMachine.transition('CloseCustomerPopup');
                    log('Transitioned StateMachine to CloseCustomerPopup');
                    return true;
                }
            } catch (e) {}
        }

        if (window.awingUIActions) {
            try {
                if (typeof window.awingUIActions.SubmitAuthenticationForm === 'function') {
                    window.awingUIActions.SubmitAuthenticationForm();
                    log('Triggered awingUIActions.SubmitAuthenticationForm()');
                    return true;
                }
            } catch (e) {}
        }

        return false;
    }

    // Step 5: DOM Element Clicking (Buttons, Banner links, Connect triggers)
    function clickConnectTriggers() {
        const prioritizedSelectors = [
            '#acceptconnection_BannerOverflow',
            '#connect-btn',
            '#acceptconnection',
            '#btnConnect',
            '#btn-connect',
            '.connect-button',
            'button.btn-primary',
            'button.btn-connect',
            'button[id*="connect"]',
            'button[id*="Connect"]',
            'a[id*="connect"]',
            'a[id*="Connect"]',
            '#banner[onClick]',
            '#landingpage'
        ];

        for (var i = 0; i < prioritizedSelectors.length; i++) {
            var sel = prioritizedSelectors[i];
            var el = document.querySelector(sel);
            if (el && el.offsetParent !== null) {
                log('Found priority target: ' + sel);
                el.click();
                return true;
            }
        }

        const targetPhrases = [
            'kết nối internet',
            'kết nối ngay',
            'kết nối',
            'connect now',
            'connect to internet',
            'connect',
            'tiếp tục',
            'continue',
            'truy cập internet',
            'truy cập',
            'access internet',
            'xem video để kết nối',
            'xem video',
            'xem quảng cáo',
            'bỏ qua',
            'skip',
            'hoàn tất',
            'đăng nhập',
            'login'
        ];

        const candidates = document.querySelectorAll('button, input[type="submit"], a.btn, div[role="button"], span.btn');
        for (var j = 0; j < candidates.length; j++) {
            var candidate = candidates[j];
            var text = (candidate.innerText || candidate.value || '').trim().toLowerCase();
            if (text) {
                for (var k = 0; k < targetPhrases.length; k++) {
                    if (text.indexOf(targetPhrases[k]) !== -1) {
                        log('Found button by matching text: "' + text + '"');
                        candidate.click();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Step 6: Form Submission (MikroTik / DrayTek / Aruba / AWING router form)
    function submitLoginForm() {
        const frmLogin = document.getElementById('frmLogin') || document.querySelector('form[name="login"]') || document.querySelector('form');
        if (frmLogin) {
            log('Found login form (' + (frmLogin.id || frmLogin.name || 'form') + '), submitting...');
            try {
                if (typeof frmLogin.submit === 'function') {
                    frmLogin.submit();
                    notifySuccess('Form Submitted');
                    return true;
                }
            } catch (e) {
                try {
                    HTMLFormElement.prototype.submit.call(frmLogin);
                    notifySuccess('Form Submitted via Prototype');
                    return true;
                } catch (err) {}
            }
        }
        return false;
    }

    function checkSuccessState() {
        const url = window.location.href.toLowerCase();
        if (url.indexOf('/success') !== -1 || url.indexOf('/state/connected') !== -1 || url.indexOf('/welcome') !== -1) {
            notifySuccess('Page URL is in success state: ' + url);
            return true;
        }
        const welcomeElem = document.getElementById('awing-captive__welcome-loading');
        if (welcomeElem && welcomeElem.style.display !== 'none') {
            notifySuccess('Welcome loading screen active');
            return true;
        }
        return false;
    }

    let attemptCount = 0;
    const maxAttempts = 50;
    const intervalMs = 500;

    const solverTimer = setInterval(function () {
        attemptCount++;

        try {
            if (checkSuccessState()) {
                clearInterval(solverTimer);
                window.__AWING_SOLVER_ACTIVE__ = false;
                return;
            }

            checkAgreements();
            handleCustomerPopup();
            unlockCountdowns();

            const smBypassed = triggerStateMachineBypass();
            const clicked = clickConnectTriggers();
            const submitted = submitLoginForm();

            if (smBypassed || clicked || submitted) {
                log('Action taken on attempt #' + attemptCount);
            }
        } catch (err) {
            log('Error in solver loop: ' + err);
        }

        if (attemptCount >= maxAttempts) {
            log('Max attempts reached. Stopping solver loop.');
            clearInterval(solverTimer);
            window.__AWING_SOLVER_ACTIVE__ = false;
        }
    }, intervalMs);

    checkAgreements();
    handleCustomerPopup();
    unlockCountdowns();
    triggerStateMachineBypass() || clickConnectTriggers() || submitLoginForm();

})();
        """.trimIndent()
    }
}
