package com.sunriseletting.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Runs after someone is signed in. Any bean of this type is called, for example to set a cookie. */
public interface SignInListener {

    void onSignIn(SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response);
}
