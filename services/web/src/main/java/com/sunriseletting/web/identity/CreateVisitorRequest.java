package com.sunriseletting.web.identity;

/** The body of identity's create Visitor call. {@link #toString()} leaves out the personal details and passwords. */
public record CreateVisitorRequest(String title, String titleOther, String firstName, String surname, String email,
        String phone, String password, String confirmPassword) {

    @Override
    public String toString() {
        return "CreateVisitorRequest[redacted]";
    }
}
