package com.sunriseletting.web.account;

import com.sunriseletting.web.identity.CreateVisitorRequest;
import jakarta.servlet.http.HttpServletRequest;

/** What was submitted, exactly as typed. {@link #toString()} leaves out the personal details and passwords. */
public record CreateAccountForm(String title, String titleOther, String firstName, String surname, String email,
        String phone, String password, String confirmPassword) {

    public CreateAccountForm {
        title = orEmpty(title);
        titleOther = orEmpty(titleOther);
        firstName = orEmpty(firstName);
        surname = orEmpty(surname);
        email = orEmpty(email);
        phone = orEmpty(phone);
        password = orEmpty(password);
        confirmPassword = orEmpty(confirmPassword);
    }

    static CreateAccountForm empty() {
        return new CreateAccountForm(null, null, null, null, null, null, null, null);
    }

    /** For code that runs outside Spring MVC's data binding. */
    static CreateAccountForm from(HttpServletRequest request) {
        return new CreateAccountForm(request.getParameter("title"), request.getParameter("titleOther"),
                request.getParameter("firstName"), request.getParameter("surname"), request.getParameter("email"),
                request.getParameter("phone"), request.getParameter("password"), request.getParameter("confirmPassword"));
    }

    /** What goes in the model: the page must never have a password to render. */
    CreateAccountForm withoutPasswords() {
        return new CreateAccountForm(title, titleOther, firstName, surname, email, phone, null, null);
    }

    CreateVisitorRequest toRequest() {
        return new CreateVisitorRequest(title, titleOther, firstName, surname, email, phone, password, confirmPassword);
    }

    @Override
    public String toString() {
        return "CreateAccountForm[redacted]";
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
