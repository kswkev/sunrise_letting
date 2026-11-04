package com.sunriseletting.web.security;

/** Who a signed-in account is. The names are the ones identity uses. */
public enum AccountRole {

    VISITOR;

    /** The Spring Security authority for this role. */
    String authority() {
        return "ROLE_" + name();
    }
}
