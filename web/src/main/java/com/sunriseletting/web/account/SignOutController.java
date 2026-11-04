package com.sunriseletting.web.account;

import com.sunriseletting.web.security.SignOutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/** Sign out. A stale or missing CSRF token never gets here: see {@code SignOutExpiredFormResponder}. */
@Controller
public class SignOutController {

    private final SignOutService signOutService;

    SignOutController(SignOutService signOutService) {
        this.signOutService = signOutService;
    }

    @PostMapping("/sign-out")
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        signOutService.signOut(request, response);
    }
}
