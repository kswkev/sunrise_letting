package com.sunriseletting.web.account;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.sunriseletting.web.identity.CreateVisitorResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.properties.PropertiesController;
import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.RememberMeLogin;
import com.sunriseletting.web.security.ReturnAddress;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.security.SignInDestinations;
import com.sunriseletting.web.security.SignInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The Create an account page. A form whose CSRF token has expired is handled by {@link CreateAccountExpiredFormResponder}.
 * The submitted form is read from the request, not bound as a model attribute, so its passwords can't reach the view.
 */
@Controller
@RequestMapping("/create-account")
public class CreateAccountController {

    private static final Logger log = LoggerFactory.getLogger(CreateAccountController.class);

    private final IdentityClient identityClient;

    private final SignInService signInService;

    private final RememberMeLogin rememberMeLogin;

    private final IdentityErrorMessages errorMessages;

    CreateAccountController(IdentityClient identityClient, SignInService signInService, RememberMeLogin rememberMeLogin, IdentityErrorMessages errorMessages) {
        this.identityClient = identityClient;
        this.signInService = signInService;
        this.rememberMeLogin = rememberMeLogin;
        this.errorMessages = errorMessages;
    }

    @GetMapping
    public ModelAndView show(@RequestParam(required = false) String returnTo, @AuthenticationPrincipal SessionPrincipal principal) {
        if (principal != null) {
            return alreadySignedIn(principal);
        }
        return new ModelAndView(CreateAccountPage.VIEW, CreateAccountPage.firstLoad(returnTo));
    }

    @PostMapping
    public ModelAndView submit(@RequestParam(required = false) String returnTo,
            @AuthenticationPrincipal SessionPrincipal principal, RedirectAttributes redirectAttributes,
            HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (principal != null) {
            return alreadySignedIn(principal);
        }
        CreateAccountForm form = CreateAccountForm.from(request);
        CreateVisitorResult result = identityClient.createVisitor(form.toRequest());
        if (result instanceof CreateVisitorResult.Created created) {
            if (!AccountRole.VISITOR.name().equals(created.role())) {
                log.warn("Identity created a Visitor account with an unexpected role: {}", created.role());
                return unavailable(form, returnTo);
            }
            signInService.signIn(created.accountId(), AccountRole.VISITOR, request, response);
            rememberMeLogin.forget(request, response);
            return redirectAfterSignUp(returnTo, redirectAttributes);
        }
        if (result instanceof CreateVisitorResult.ValidationFailed failed) {
            return messages(failed, locale)
                    .map(messages -> new ModelAndView(CreateAccountPage.VIEW, CreateAccountPage.withFieldErrors(form, returnTo, messages)))
                    .orElseGet(() -> unavailable(form, returnTo));
        }
        if (result instanceof CreateVisitorResult.NotCreated) {
            return new ModelAndView(CreateAccountPage.VIEW,
                    CreateAccountPage.withFormError(form, returnTo, CreateAccountPage.FORM_ERROR_DUPLICATE));
        }
        return unavailable(form, returnTo);
    }

    private ModelAndView unavailable(CreateAccountForm form, String returnTo) {
        return new ModelAndView(CreateAccountPage.VIEW,
                CreateAccountPage.withFormError(form, returnTo, CreateAccountPage.FORM_ERROR_UNAVAILABLE),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private ModelAndView alreadySignedIn(SessionPrincipal principal) {
        return new ModelAndView("redirect:" + SignInDestinations.whenAlreadySignedIn(principal));
    }

    private ModelAndView redirectAfterSignUp(String returnTo, RedirectAttributes redirectAttributes) {
        if (ReturnAddress.validOrNull(returnTo) == null) {
            redirectAttributes.addFlashAttribute(PropertiesController.ACCOUNT_CREATED, true);
        }
        RedirectView redirect = new RedirectView(SignInDestinations.forVisitor(returnTo), true);
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        redirect.setExposeModelAttributes(false);
        redirect.setExpandUriTemplateVariables(false);
        return new ModelAndView(redirect);
    }

    /** Each field's message, or empty if any code has no copy: an error the Visitor can't be told about can't be fixed by them. */
    private Optional<Map<String, String>> messages(CreateVisitorResult.ValidationFailed failed, Locale locale) {
        Map<String, String> messages = new HashMap<>();
        for (Map.Entry<String, String> error : failed.errors().entrySet()) {
            Optional<String> message = errorMessages.messageFor(error.getValue(), locale);
            if (message.isEmpty()) {
                return Optional.empty();
            }
            messages.put(error.getKey(), message.get());
        }
        return Optional.of(messages);
    }
}
