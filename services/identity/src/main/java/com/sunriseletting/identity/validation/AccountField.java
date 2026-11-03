package com.sunriseletting.identity.validation;

import java.util.Arrays;
import java.util.Optional;

/**
 * The account fields, with the names they have in the API.
 */
public enum AccountField {
    TITLE("title"),
    TITLE_OTHER("titleOther"),
    FIRST_NAME("firstName"),
    SURNAME("surname"),
    EMAIL("email"),
    PHONE("phone"),
    PASSWORD("password"),
    CONFIRM_PASSWORD("confirmPassword");

    private final String apiName;

    AccountField(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public static Optional<AccountField> fromApiName(String apiName) {
        return Arrays.stream(values()).filter(field -> field.apiName.equals(apiName)).findFirst();
    }
}
