/**
 * AWING Wi-Fi Captive Portal Universal Auto-Solver Script (Optimized for Ultra-Low CPU)
 * Compatible with connect.awing.vn, awifi.vn, acm.awingconnect.vn, wifi.awing.vn,
 * and partner venues: Highlands Coffee, CGV Cinemas, Trung Nguyen Legend, Vincom, Golden Gate, etc.
 */
(function () {
    'use strict';

    if (window.__AWING_SOLVER_ACTIVE__) {
        console.log('[AWING_SOLVER] Already running.');
        return;
    }
    window.__AWING_SOLVER_ACTIVE__ = true;

    console.log('[AWING_SOLVER] Starting AWING Portal Auto-Solver (Ultra-Low CPU)...');

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
        checkboxes.forEach(function (cb) {
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
        skipButtons.forEach(function (btn) {
            try {
                btn.click();
                log('Clicked customer popup skip button');
            } catch (e) {}
        });
    }

    // Step 3: Fast-forward countdowns and unlock disabled controls
    function unlockCountdowns() {
        const disabledButtons = document.querySelectorAll('button[disabled], input[type="submit"][disabled], .btn-disabled');
        disabledButtons.forEach(function (btn) {
            try {
                btn.removeAttribute('disabled');
                btn.classList.remove('btn-disabled');
                btn.classList.add('btn-active');
            } catch (e) {}
        });

        const videos = document.querySelectorAll('video');
        videos.forEach(function (v) {
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

    // Immediate action check
    if (checkSuccessState()) {
        window.__AWING_SOLVER_ACTIVE__ = false;
        return;
    }

    checkAgreements();
    handleCustomerPopup();
    unlockCountdowns();
    const immediateAction = triggerStateMachineBypass() || clickConnectTriggers() || submitLoginForm();
    if (immediateAction) {
        log('Immediate action taken on injection. Stopping solver to preserve CPU.');
        window.__AWING_SOLVER_ACTIVE__ = false;
        return;
    }

    // Throttled loop: 1200ms interval, max 15 attempts (~18s max execution)
    let attemptCount = 0;
    const maxAttempts = 15;
    const intervalMs = 1200;

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
                log('Action taken on attempt #' + attemptCount + '. Clearing interval immediately.');
                clearInterval(solverTimer);
                window.__AWING_SOLVER_ACTIVE__ = false;
                return;
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
})();
