package com.sunriseletting.web.testsupport;

import java.util.List;

import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.SessionPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Who is signed in, for page tests. */
public final class SignedInUsers {

    private SignedInUsers() {
    }

    public static Authentication visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }

    public static Authentication propertyManager(boolean temporaryPassword) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, temporaryPassword), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROPERTY_MANAGER")));
    }
}
