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

/** Re-shows the Create an account form when its CSRF token is missing or no longer valid (design-system.md 3.5). */
@Component
class CreateAccountExpiredFormResponder implements ExpiredFormResponder {

    private final ViewResolver viewResolver;

    CreateAccountExpiredFormResponder(@Qualifier("thymeleafViewResolver") ViewResolver viewResolver) {
        this.viewResolver = viewResolver;
    }

    @Override
    public boolean handles(HttpServletRequest request) {
        return "/create-account".equals(request.getRequestURI());
    }

    @Override
    public void respond(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            response.sendRedirect(SignInDestinations.whenAlreadySignedIn(principal));
            return;
        }
        try {
            viewResolver.resolveViewName(CreateAccountPage.VIEW, request.getLocale())
                    .render(CreateAccountPage.withFormError(CreateAccountForm.from(request), request.getParameter("returnTo"),
                            CreateAccountPage.FORM_ERROR_EXPIRED), request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
