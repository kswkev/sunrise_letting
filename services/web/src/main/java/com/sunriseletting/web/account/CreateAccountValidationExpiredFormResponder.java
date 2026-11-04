package com.sunriseletting.web.account;

import com.sunriseletting.web.security.ExpiredFormResponder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** An inline check whose CSRF token has expired changes nothing: the message appears on the next submit (design-system.md 3.5). */
@Component
class CreateAccountValidationExpiredFormResponder implements ExpiredFormResponder {

    @Override
    public boolean handles(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/create-account/validate/");
    }

    @Override
    public void respond(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }
}
