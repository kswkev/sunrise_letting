package com.sunriseletting.web.account;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.sunriseletting.web.identity.AuthenticationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.security.RememberMeLogin;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.security.SignInDestinations;
import com.sunriseletting.web.security.SignInService;
import com.sunriseletting.web.security.SignedInMarker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The shared Sign in page. A form whose CSRF token has expired is handled by {@link SignInExpiredFormResponder}.
 * The password is a plain request parameter, not a model attribute, so it can't reach the view or a redirect.
 */
@Controller
@RequestMapping("/sign-in")
public class SignInController {

    private final IdentityClient identityClient;

    private final SignInService signInService;

    private final RememberMeLogin rememberMeLogin;

    private final SignedInMarker signedInMarker;

    private final MessageSource messages;

    SignInController(IdentityClient identityClient, SignInService signInService, RememberMeLogin rememberMeLogin, SignedInMarker signedInMarker,
            MessageSource messages) {
        this.identityClient = identityClient;
        this.signInService = signInService;
        this.rememberMeLogin = rememberMeLogin;
        this.signedInMarker = signedInMarker;
        this.messages = messages;
    }

    @GetMapping
    public ModelAndView show(@RequestParam(required = false) String returnTo, @RequestParam(required = false) String timedOut,
            @AuthenticationPrincipal SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        SignInPage.Values values = new SignInPage.Values("", false, returnTo, timedOut);
        if (principal != null) {
            String destination = values.isTimedOut() ? SignInDestinations.afterSignIn(principal, returnTo) : SignInDestinations.whenAlreadySignedIn(principal);
            return new ModelAndView(redirect(destination, HttpStatus.FOUND));
        }
        if (values.isTimedOut()) {
            signedInMarker.clear(request, response);
        }
        return new ModelAndView(SignInPage.VIEW, SignInPage.firstLoad(values));
    }

    @PostMapping
    public ModelAndView submit(@RequestParam(defaultValue = "") String email, @RequestParam(defaultValue = "") String password,
            @RequestParam(name = "remember-me", required = false) String rememberMe, @RequestParam(required = false) String returnTo,
            @RequestParam(required = false) String timedOut, @AuthenticationPrincipal SessionPrincipal principal,
            HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (principal != null) {
            return new ModelAndView(redirect(SignInDestinations.whenAlreadySignedIn(principal), HttpStatus.FOUND));
        }
        SignInPage.Values values = new SignInPage.Values(email, rememberMe != null, returnTo, timedOut);
        AuthenticationResult result = identityClient.authenticate(email, password);
        if (result instanceof AuthenticationResult.SignedIn signedIn) {
            SessionPrincipal account = new SessionPrincipal(signedIn.accountId(), signedIn.role(), signedIn.temporaryPassword());
            signInService.signIn(account, request, response);
            if (values.rememberMe()) {
                rememberMeLogin.remember(account, request, response);
            }
            return new ModelAndView(redirect(SignInDestinations.afterSignIn(account, returnTo), HttpStatus.SEE_OTHER));
        }
        if (result instanceof AuthenticationResult.FieldErrors failed) {
            return new ModelAndView(SignInPage.VIEW, SignInPage.withFieldErrors(values, messages(failed, locale)));
        }
        if (result instanceof AuthenticationResult.InvalidCredentials) {
            return new ModelAndView(SignInPage.VIEW, SignInPage.withFormError(values, SignInPage.FORM_ERROR_INVALID));
        }
        return new ModelAndView(SignInPage.VIEW, SignInPage.withFormError(values, SignInPage.FORM_ERROR_UNAVAILABLE),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private RedirectView redirect(String destination, HttpStatus status) {
        RedirectView redirect = new RedirectView(destination, true);
        redirect.setStatusCode(status);
        redirect.setExposeModelAttributes(false);
        redirect.setExpandUriTemplateVariables(false);
        return redirect;
    }

    /** This page's copy, not sign-up's: "Enter your password" rather than "Enter a password". */
    private Map<String, String> messages(AuthenticationResult.FieldErrors failed, Locale locale) {
        Map<String, String> fieldMessages = new HashMap<>();
        failed.errors().forEach((field, code) -> fieldMessages.put(field, messages.getMessage("sign-in.error." + code, null, locale)));
        return fieldMessages;
    }
}
