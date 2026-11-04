package com.sunriseletting.web.identity;

/** The fields of a Visitor account, in form order, with the names identity's API uses for them. */
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
}
