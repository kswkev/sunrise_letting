package com.sunriseletting.web.security;

/**
 * Lets one request at a time, across every replica, exchange a remember-me cookie. Identity treats a second use
 * of a token as theft and revokes all of the account's tokens, so parallel requests with the same cookie must
 * never both reach it. A request that is turned away goes on as signed out, without touching the cookie.
 */
public interface RememberMeExchangeGate {

    /** True if this request may exchange the series. After a successful exchange the gate stays shut for a short while. */
    boolean tryEnter(String series);

    /** Reopens the gate after an exchange that replaced nothing, so the browser can try again. */
    void reopen(String series);
}
