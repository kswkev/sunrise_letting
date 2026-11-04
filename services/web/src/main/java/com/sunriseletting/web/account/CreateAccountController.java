package com.sunriseletting.web.account;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.sunriseletting.web.identity.CreateVisitorResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.properties.PropertiesController;
import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.ReturnAddress;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.security.SignInDestinations;
import com.sunriseletting.web.security.SignInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/** The Create an account page. A form whose CSRF token has expired is handled by {@link CreateAccountExpiredFormResponder}. */
@Controller
@RequestMapping("/create-account")
public class CreateAccountController {

    private final IdentityClient identityClient;

    private final SignInService signInService;

    private final IdentityErrorMessages errorMessages;

    CreateAccountController(IdentityClient identityClient, SignInService signInService, IdentityErrorMessages errorMessages) {
        this.identityClient = identityClient;
        this.signInService = signInService;
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
    public ModelAndView submit(@ModelAttribute("submitted") CreateAccountForm form, @RequestParam(required = false) String returnTo,
            @AuthenticationPrincipal SessionPrincipal principal, RedirectAttributes redirectAttributes,
            HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (principal != null) {
            return alreadySignedIn(principal);
        }
        CreateVisitorResult result = identityClient.createVisitor(form.toRequest());
        if (result instanceof CreateVisitorResult.Created created) {
            signInService.signIn(new SessionPrincipal(created.accountId(), AccountRole.valueOf(created.role()), false), request, response);
            return redirectAfterSignUp(returnTo, redirectAttributes);
        }
        if (result instanceof CreateVisitorResult.ValidationFailed failed) {
            return new ModelAndView(CreateAccountPage.VIEW, CreateAccountPage.withFieldErrors(form, returnTo, messages(failed, locale)));
        }
        if (result instanceof CreateVisitorResult.NotCreated) {
            return new ModelAndView(CreateAccountPage.VIEW,
                    CreateAccountPage.withFormError(form, returnTo, CreateAccountPage.FORM_ERROR_DUPLICATE));
        }
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

    private Map<String, String> messages(CreateVisitorResult.ValidationFailed failed, Locale locale) {
        Map<String, String> messages = new HashMap<>();
        failed.errors().forEach((field, code) -> messages.put(field, errorMessages.messageFor(code, locale)));
        return messages;
    }
}
