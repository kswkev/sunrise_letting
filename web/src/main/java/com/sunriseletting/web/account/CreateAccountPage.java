package com.sunriseletting.web.account;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sunriseletting.web.forms.SummaryError;
import com.sunriseletting.web.security.ReturnAddress;

/** The model for each state of the Create an account page. */
final class CreateAccountPage {

    static final String VIEW = "account/create-account";

    static final String FORM_ERROR_DUPLICATE = "duplicate";

    static final String FORM_ERROR_UNAVAILABLE = "unavailable";

    static final String FORM_ERROR_EXPIRED = "expired";

    private CreateAccountPage() {
    }

    static Map<String, Object> firstLoad(String returnTo) {
        return model(CreateAccountForm.empty(), returnTo, Map.of(), List.of(), null);
    }

    /** One error per field in {@code fieldErrors}, keyed by field name, with the summary in form order. */
    static Map<String, Object> withFieldErrors(CreateAccountForm form, String returnTo, Map<String, String> fieldErrors) {
        List<SummaryError> summary = Arrays.stream(CreateAccountField.values())
                .filter(field -> fieldErrors.containsKey(field.apiName()))
                .map(field -> new SummaryError(field.inputId(), fieldErrors.get(field.apiName())))
                .toList();
        return model(form, returnTo, fieldErrors, summary, null);
    }

    static Map<String, Object> withFormError(CreateAccountForm form, String returnTo, String formError) {
        return model(form, returnTo, Map.of(), List.of(), formError);
    }

    private static Map<String, Object> model(CreateAccountForm form, String returnTo, Map<String, String> fieldErrors,
            List<SummaryError> summaryErrors, String formError) {
        Map<String, Object> model = new HashMap<>();
        model.put("form", form.withoutPasswords());
        model.put("returnTo", ReturnAddress.validOrNull(returnTo));
        model.put("fieldErrors", fieldErrors);
        model.put("summaryErrors", summaryErrors);
        model.put("formError", formError);
        model.put("hasErrors", !summaryErrors.isEmpty() || formError != null);
        return model;
    }
}
