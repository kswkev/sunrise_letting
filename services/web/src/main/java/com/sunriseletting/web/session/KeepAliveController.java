package com.sunriseletting.web.session;

import com.sunriseletting.web.security.SessionPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Called by the time-out script. The request itself resets the idle timer. A session that has ended gets a
 * bare 401, so the script can tell, also on a stale CSRF token: see {@code KeepAliveExpiredFormResponder}.
 */
@Controller
public class KeepAliveController {

    @PostMapping("/session/keep-alive")
    public ResponseEntity<Void> keepAlive(@AuthenticationPrincipal SessionPrincipal principal) {
        return principal != null ? ResponseEntity.noContent().build() : ResponseEntity.status(401).build();
    }
}
