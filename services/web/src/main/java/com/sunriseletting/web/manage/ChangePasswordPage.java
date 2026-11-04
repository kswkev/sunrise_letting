package com.sunriseletting.web.manage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sunriseletting.web.forms.SummaryError;

/** The model for each state of the Change your password page. Nothing here can hold a password, so none can be rendered. */
final class ChangePasswordPage {

    static final String VIEW = "manage/change-password";

    /** Identity's field names and the ids of their inputs, in field order. */
    private static final Map<String, String> INPUT_IDS = Map.of("newPassword", "new-password", "confirmNewPassword", "confirm-new-password");

    private static final List<String> FIELD_ORDER = List.of("newPassword", "confirmNewPassword");

    private ChangePasswordPage() {
    }

    static Map<String, Object> firstLoad() {
        return model(Map.of(), List.of(), false);
    }

    static Map<String, Object> withFieldErrors(Map<String, String> fieldErrors) {
        List<SummaryError> summary = new ArrayList<>();
        for (String field : FIELD_ORDER) {
            if (fieldErrors.containsKey(field)) {
                summary.add(new SummaryError(INPUT_IDS.get(field), fieldErrors.get(field)));
            }
        }
        return model(fieldErrors, summary, false);
    }

    static Map<String, Object> unavailable() {
        return model(Map.of(), List.of(), true);
    }

    private static Map<String, Object> model(Map<String, String> fieldErrors, List<SummaryError> summaryErrors, boolean unavailable) {
        return Map.of("fieldErrors", fieldErrors, "summaryErrors", summaryErrors, "unavailable", unavailable,
                "hasErrors", !summaryErrors.isEmpty() || unavailable);
    }
}
