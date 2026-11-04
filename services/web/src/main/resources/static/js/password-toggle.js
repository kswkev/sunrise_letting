// Password field (docs/design/design-system.md 4.17): the show/hide toggle, and keeping the error
// styling, aria-invalid and aria-describedby in step after inline validation swaps a message into the error slot.
// Without this script the toggle stays hidden and the field is a normal masked input.
(function () {
    'use strict';

    function inputOf(toggle) {
        return document.getElementById(toggle.getAttribute('aria-controls'));
    }

    function setShown(toggle, shown) {
        inputOf(toggle).type = shown ? 'text' : 'password';
        // The first child is the button's text; the visually hidden suffix after it stays as it is.
        toggle.firstChild.nodeValue = shown ? 'Hide' : 'Show';
    }

    document.querySelectorAll('.password-field__toggle').forEach(function (toggle) {
        toggle.hidden = false;
        toggle.addEventListener('click', function () {
            var shown = inputOf(toggle).type === 'password';
            setShown(toggle, shown);
            toggle.closest('.password-field').querySelector('.password-field__status').textContent =
                shown ? 'Your password is visible' : 'Your password is hidden';
        });
    });

    document.addEventListener('submit', function (event) {
        event.target.querySelectorAll('.password-field__toggle').forEach(function (toggle) {
            setShown(toggle, false);
        });
    });

    document.addEventListener('htmx:afterSwap', function (event) {
        var slot = event.detail.target;
        if (!slot.classList.contains('password-field__error-slot')) {
            return;
        }
        var group = slot.closest('.password-field');
        var input = group.querySelector('.password-field__input');
        if (slot.querySelector('.error-message')) {
            group.classList.add('form-group--error');
            input.setAttribute('aria-invalid', 'true');
            setDescribedByError(input, true);
        } else {
            group.classList.remove('form-group--error');
            input.removeAttribute('aria-invalid');
            setDescribedByError(input, false);
        }
    });

    // The server lists the error's id only when it renders one, so a message swapped in has to be added here.
    function setDescribedByError(input, hasError) {
        var errorId = input.id + '-error';
        var ids = (input.getAttribute('aria-describedby') || '').split(/\s+/).filter(function (id) {
            return id && id !== errorId;
        });
        if (hasError) {
            ids.push(errorId);
        }
        if (ids.length) {
            input.setAttribute('aria-describedby', ids.join(' '));
        } else {
            input.removeAttribute('aria-describedby');
        }
    }
})();
