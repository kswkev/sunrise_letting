package com.sunriseletting.web.account;

import java.io.IOException;

import com.sunriseletting.web.security.ExpiredFormResponder;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.security.SignInDestinations;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ViewResolver;

/** Re-shows the Sign in form when its CSRF token is missing or no longer valid (design-system.md 3.5). */
@Component
class SignInExpiredFormResponder implements ExpiredFormResponder {

    private final ViewResolver viewResolver;

    SignInExpiredFormResponder(@Qualifier("thymeleafViewResolver") ViewResolver viewResolver) {
        this.viewResolver = viewResolver;
    }

    @Override
    public boolean handles(HttpServletRequest request) {
        return "/sign-in".equals(request.getRequestURI());
    }

    @Override
    public void respond(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            response.sendRedirect(SignInDestinations.whenAlreadySignedIn(principal));
            return;
        }
        String email = request.getParameter("email");
        SignInPage.Values values = new SignInPage.Values(email == null ? "" : email, request.getParameter("remember-me") != null,
                request.getParameter("returnTo"), request.getParameter("timedOut"));
        try {
            viewResolver.resolveViewName(SignInPage.VIEW, request.getLocale()).render(SignInPage.expired(values), request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
