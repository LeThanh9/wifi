/**
 * AWING Wi-Fi Captive Portal Universal Auto-Solver Script
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
        checkboxes.forEach(cb => {
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
        skipButtons.forEach(btn => {
            try {
                btn.click();
                log('Clicked customer popup skip button');
            } catch (e) {}
        });
    }

    // Step 3: Fast-forward countdowns and unlock disabled controls
    function unlockCountdowns() {
        // Remove disabled flags from buttons
        const disabledButtons = document.querySelectorAll('button[disabled], input[type="submit"][disabled], .btn-disabled');
        disabledButtons.forEach(btn => {
            try {
                btn.removeAttribute('disabled');
                btn.classList.remove('btn-disabled');
                btn.classList.add('btn-active');
            } catch (e) {}
        });

        // Fast-forward video ads if present
        const videos = document.querySelectorAll('video');
        videos.forEach(v => {
            try {
                if (!v.paused && v.duration > 0 && v.currentTime < v.duration) {
                    v.currentTime = v.duration;
                    v.dispatchEvent(new Event('ended', { bubbles: true }));
                    log('Fast-forwarded ad video to end');
                }
            } catch (e) {}
        });

        // Acceleration through AWING internal events if available
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
        // If modern AWING framework is loaded
        if (window.awingStateMachineContextActions) {
            try {
                // If submit/authen is ready
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
        // High priority selector list for AWING templates
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

        for (const sel of prioritizedSelectors) {
            const el = document.querySelector(sel);
            if (el && el.offsetParent !== null) { // visible
                log('Found priority target: ' + sel);
                el.click();
                return true;
            }
        }

        // Search buttons by text content
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
            'xem quảng cáo',
            'bỏ qua',
            'skip',
            'hoàn tất',
            'đăng nhập',
            'login'
        ];

        const candidates = document.querySelectorAll('button, input[type="submit"], a.btn, div[role="button"], span.btn');
        for (const candidate of candidates) {
            const text = (candidate.innerText || candidate.value || '').trim().toLowerCase();
            if (text && targetPhrases.some(phrase => text.includes(phrase))) {
                log('Found button by matching text: "' + text + '"');
                candidate.click();
                return true;
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
                // If input named 'submit' masks form.submit
                try {
                    HTMLFormElement.prototype.submit.call(frmLogin);
                    notifySuccess('Form Submitted via Prototype');
                    return true;
                } catch (err) {}
            }
        }
        return false;
    }

    // Check if already connected / successful page
    function checkSuccessState() {
        const url = window.location.href.toLowerCase();
        if (url.includes('/success') || url.includes('/state/connected') || url.includes('/welcome')) {
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

    // Main execution cycle
    let attemptCount = 0;
    const maxAttempts = 50; // Run for up to ~25 seconds
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

    // Initial immediate kick
    checkAgreements();
    handleCustomerPopup();
    unlockCountdowns();
    triggerStateMachineBypass() || clickConnectTriggers() || submitLoginForm();

})();
