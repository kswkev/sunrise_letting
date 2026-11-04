package com.sunriseletting.web.account;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.sunriseletting.web.identity.AccountField;
import com.sunriseletting.web.identity.FieldValidationResult;
import com.sunriseletting.web.identity.IdentityClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Inline validation for the sign-up form (design-system.md 5.4), for HTMX only. Every field but the
 * passwords answers with its whole group. The passwords answer with only their message, so no
 * response ever holds a password. A token that has expired is handled by
 * {@link CreateAccountValidationExpiredFormResponder}.
 */
@Controller
public class CreateAccountValidationController {

    private static final String OTHER_TITLE = "Other";

    private final IdentityClient identityClient;

    private final IdentityErrorMessages errorMessages;

    CreateAccountValidationController(IdentityClient identityClient, IdentityErrorMessages errorMessages) {
        this.identityClient = identityClient;
        this.errorMessages = errorMessages;
    }

    /** Returning null means the response is complete: a 204, which HTMX doesn't swap. */
    @PostMapping("/create-account/validate/{inputId}")
    public ModelAndView validate(@PathVariable String inputId, HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (request.getHeader("HX-Request") == null) {
            RedirectView redirect = new RedirectView("/create-account", true);
            redirect.setStatusCode(HttpStatus.SEE_OTHER);
            return new ModelAndView(redirect);
        }
        CreateAccountField field = CreateAccountField.withInputId(inputId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String value = request.getParameter(field.apiName());
        if (value == null || value.isBlank() || isIgnored(field, request)) {
            return render(field, null, request);
        }
        FieldValidationResult result = check(field, value, request);
        if (result instanceof FieldValidationResult.Invalid invalid) {
            return render(field, errorMessages.messageFor(invalid.code(), locale), request);
        }
        if (result instanceof FieldValidationResult.Valid) {
            return render(field, null, request);
        }
        response.setStatus(HttpStatus.NO_CONTENT.value());
        return null;
    }

    /** Other title only counts when Title is "Other": identity ignores it otherwise on submit, so no error should show. */
    private boolean isIgnored(CreateAccountField field, HttpServletRequest request) {
        if (field != CreateAccountField.TITLE_OTHER) {
            return false;
        }
        String title = request.getParameter(CreateAccountField.TITLE.apiName());
        return title == null || !OTHER_TITLE.equals(title.strip());
    }

    private FieldValidationResult check(CreateAccountField field, String value, HttpServletRequest request) {
        if (field.accountField() == AccountField.CONFIRM_PASSWORD) {
            String password = request.getParameter(CreateAccountField.PASSWORD.apiName());
            return identityClient.validateField(field.accountField(), value, password == null ? "" : password);
        }
        return identityClient.validateField(field.accountField(), value);
    }

    private ModelAndView render(CreateAccountField field, String message, HttpServletRequest request) {
        Map<String, Object> model = new HashMap<>();
        if (field.isPassword()) {
            model.put("id", field.inputId());
            model.put("error", message);
            return new ModelAndView("fragments/form :: errorMessage", model);
        }
        Map<String, String> fieldErrors = new HashMap<>();
        if (message != null) {
            fieldErrors.put(field.apiName(), message);
        }
        model.put("form", CreateAccountForm.from(request).withoutPasswords());
        model.put("fieldErrors", fieldErrors);
        return new ModelAndView("account/create-account-fields :: field-" + field.inputId(), model);
    }
}
