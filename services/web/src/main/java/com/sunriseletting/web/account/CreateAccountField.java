package com.sunriseletting.web.account;

import com.sunriseletting.web.identity.AccountField;

/** The sign-up form's fields, in form order: each one's input id, and the field identity knows it as. */
enum CreateAccountField {

    TITLE("title", AccountField.TITLE),
    TITLE_OTHER("title-other", AccountField.TITLE_OTHER),
    FIRST_NAME("first-name", AccountField.FIRST_NAME),
    SURNAME("surname", AccountField.SURNAME),
    EMAIL("email", AccountField.EMAIL),
    PHONE("phone", AccountField.PHONE),
    PASSWORD("password", AccountField.PASSWORD),
    CONFIRM_PASSWORD("confirm-password", AccountField.CONFIRM_PASSWORD);

    private final String inputId;

    private final AccountField accountField;

    CreateAccountField(String inputId, AccountField accountField) {
        this.inputId = inputId;
        this.accountField = accountField;
    }

    String inputId() {
        return inputId;
    }

    AccountField accountField() {
        return accountField;
    }

    /** The form's parameter name, and the key identity reports errors under. */
    String apiName() {
        return accountField.apiName();
    }
}
