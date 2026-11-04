package com.sunriseletting.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

/** The keep-alive's CSRF token is stale when its session has ended. The script needs a bare 401 then, not a redirect (design-system.md 4.20). */
class KeepAliveExpiredFormResponder implements ExpiredFormResponder {

    @Override
    public boolean handles(HttpServletRequest request) {
        return "/session/keep-alive".equals(request.getRequestURI());
    }

    @Override
    public void respond(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}
