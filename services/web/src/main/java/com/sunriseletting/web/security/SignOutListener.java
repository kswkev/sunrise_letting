package com.sunriseletting.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Runs on every sign out, including one with a stale token or a session that has already ended. Any bean of this type is called. */
public interface SignOutListener {

    void onSignOut(HttpServletRequest request, HttpServletResponse response);
}
