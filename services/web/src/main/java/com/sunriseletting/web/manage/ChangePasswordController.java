package com.sunriseletting.web.manage;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.sunriseletting.web.identity.ChangePasswordResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.security.SignInDestinations;
import com.sunriseletting.web.security.SignInService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The page that replaces a temporary password at first sign-in. The access rules let only Property Managers reach it.
 * The passwords are plain request parameters, not model attributes, so they can't reach the view or a redirect.
 */
@Controller
@RequestMapping("/manage/change-password")
public class ChangePasswordController {

    private final IdentityClient identityClient;

    private final SignInService signInService;

    private final MessageSource messages;

    ChangePasswordController(IdentityClient identityClient, SignInService signInService, MessageSource messages) {
        this.identityClient = identityClient;
        this.signInService = signInService;
        this.messages = messages;
    }

    @GetMapping
    public ModelAndView show(@AuthenticationPrincipal SessionPrincipal principal) {
        if (!principal.temporaryPassword()) {
            return new ModelAndView(redirect(principal, HttpStatus.FOUND));
        }
        return new ModelAndView(ChangePasswordPage.VIEW, ChangePasswordPage.firstLoad());
    }

    @PostMapping
    public ModelAndView submit(@RequestParam(defaultValue = "") String newPassword, @RequestParam(defaultValue = "") String confirmNewPassword,
            @AuthenticationPrincipal SessionPrincipal principal, RedirectAttributes redirectAttributes,
            HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (!principal.temporaryPassword()) {
            return new ModelAndView(redirect(principal, HttpStatus.FOUND));
        }
        ChangePasswordResult result = identityClient.changePassword(principal.accountId(), newPassword, confirmNewPassword);
        if (result instanceof ChangePasswordResult.Changed) {
            redirectAttributes.addFlashAttribute(ManageController.PASSWORD_CHANGED, true);
            return new ModelAndView(leaveThePage(principal, request, response));
        }
        if (result instanceof ChangePasswordResult.NoTemporaryPassword) {
            return new ModelAndView(leaveThePage(principal, request, response));
        }
        if (result instanceof ChangePasswordResult.FieldErrors failed) {
            return new ModelAndView(ChangePasswordPage.VIEW, ChangePasswordPage.withFieldErrors(messages(failed, locale)));
        }
        return new ModelAndView(ChangePasswordPage.VIEW, ChangePasswordPage.unavailable(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    private RedirectView leaveThePage(SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        SessionPrincipal permanent = signInService.passwordIsPermanent(principal, request, response);
        return redirect(permanent, HttpStatus.SEE_OTHER);
    }

    private RedirectView redirect(SessionPrincipal principal, HttpStatus status) {
        RedirectView redirect = new RedirectView(SignInDestinations.whenAlreadySignedIn(principal), true);
        redirect.setStatusCode(status);
        redirect.setExposeModelAttributes(false);
        redirect.setExpandUriTemplateVariables(false);
        return redirect;
    }

    private Map<String, String> messages(ChangePasswordResult.FieldErrors failed, Locale locale) {
        Map<String, String> fieldMessages = new HashMap<>();
        failed.errors().forEach((field, code) -> fieldMessages.put(field, messages.getMessage("change-password.error." + code, null, locale)));
        return fieldMessages;
    }
}
