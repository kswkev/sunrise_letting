// Session time-out warning (docs/design/design-system.md 4.20). Each tab runs its own timer, restarted by
// every completed HTMX request, because those reset the server's idle timer too. Every value comes from the
// dialog's data- attributes, so this also works for time-outs other than a Visitor's.
(function () {
    'use strict';

    var dialog = document.getElementById('timeout-dialog');
    if (!dialog) {
        return;
    }

    // How long after the time-out to wait, so that the server session has certainly ended.
    var MARGIN_MILLIS = 5000;

    var timeoutMillis = Number(dialog.dataset.timeoutSeconds) * 1000;
    var warningMillis = Number(dialog.dataset.warningSeconds) * 1000;
    var warningTimer;
    var expiryTimer;
    var focusedBeforeOpening;
    var keepAliveInFlight = false;
    var closedByThisScript = false;

    function goToTimedOutAddress() {
        location.replace(dialog.dataset.timedOutUrl + '&returnTo=' + encodeURIComponent(location.pathname + location.search));
    }

    function startTimers() {
        clearTimeout(warningTimer);
        clearTimeout(expiryTimer);
        warningTimer = setTimeout(openDialog, timeoutMillis - warningMillis);
        expiryTimer = setTimeout(goToTimedOutAddress, timeoutMillis + MARGIN_MILLIS);
    }

    function openDialog() {
        if (!dialog.open) {
            focusedBeforeOpening = document.activeElement;
            dialog.showModal();
        }
    }

    function returnFocus() {
        var target = document.contains(focusedBeforeOpening) ? focusedBeforeOpening : document.querySelector('main');
        target.focus();
    }

    function staySignedIn() {
        if (keepAliveInFlight) {
            return;
        }
        keepAliveInFlight = true;
        fetch(dialog.dataset.keepAliveUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: '_csrf=' + encodeURIComponent(dialog.querySelector('form input[name="_csrf"]').value)
        }).then(function (response) {
            if (response.status !== 204) {
                goToTimedOutAddress();
                return;
            }
            keepAliveInFlight = false;
            if (dialog.open) {
                closedByThisScript = true;
                dialog.close();
            }
            startTimers();
            returnFocus();
        }).catch(goToTimedOutAddress);
    }

    dialog.querySelector('.timeout-dialog__stay').addEventListener('click', staySignedIn);

    dialog.addEventListener('cancel', function (event) {
        event.preventDefault();
        staySignedIn();
    });

    // Without a recent user action on the page, Chrome closes the dialog on Escape with no cancel event. That still counts.
    dialog.addEventListener('close', function () {
        if (closedByThisScript) {
            closedByThisScript = false;
            return;
        }
        staySignedIn();
    });

    document.addEventListener('htmx:afterOnLoad', startTimers);

    startTimers();
})();
