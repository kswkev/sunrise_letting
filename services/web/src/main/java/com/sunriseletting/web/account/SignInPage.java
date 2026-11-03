package com.sunriseletting.web.account;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sunriseletting.web.forms.SummaryError;
import com.sunriseletting.web.security.ReturnAddress;

/** The model for each state of the Sign in page. Nothing here can hold a password, so none can be rendered. */
final class SignInPage {

    static final String VIEW = "account/sign-in";

    static final String FORM_ERROR_INVALID = "invalid";

    static final String FORM_ERROR_UNAVAILABLE = "unavailable";

    static final String FORM_ERROR_EXPIRED = "expired";

    private SignInPage() {
    }

    /** What the form carries from one render to the next. */
    record Values(String email, boolean rememberMe, String returnTo, String timedOut) {

        boolean isTimedOut() {
            return "true".equals(timedOut);
        }
    }

    static Map<String, Object> firstLoad(Values values) {
        return model(values, values.isTimedOut(), Map.of(), List.of(), null);
    }

    static Map<String, Object> withFieldErrors(Values values, Map<String, String> fieldErrors) {
        List<SummaryError> summary = new ArrayList<>();
        for (String field : List.of("email", "password")) {
            if (fieldErrors.containsKey(field)) {
                summary.add(new SummaryError(field, fieldErrors.get(field)));
            }
        }
        return model(values, values.isTimedOut(), fieldErrors, summary, null);
    }

    static Map<String, Object> withFormError(Values values, String formError) {
        return model(values, values.isTimedOut(), Map.of(), List.of(), formError);
    }

    /** Only the expired-form error is shown, and the marker is dropped so the banner doesn't come back on a later submit. */
    static Map<String, Object> expired(Values values) {
        return model(values, false, Map.of(), List.of(), FORM_ERROR_EXPIRED);
    }

    private static Map<String, Object> model(Values values, boolean timedOut, Map<String, String> fieldErrors,
            List<SummaryError> summaryErrors, String formError) {
        Map<String, Object> model = new HashMap<>();
        model.put("email", values.email());
        model.put("rememberMe", values.rememberMe());
        model.put("returnTo", ReturnAddress.validOrNull(values.returnTo()));
        model.put("timedOut", timedOut);
        model.put("fieldErrors", fieldErrors);
        model.put("summaryErrors", summaryErrors);
        model.put("formError", formError);
        model.put("hasErrors", !summaryErrors.isEmpty() || formError != null);
        return model;
    }
}
