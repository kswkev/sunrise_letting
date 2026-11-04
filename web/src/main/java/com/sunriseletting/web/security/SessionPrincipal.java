package com.sunriseletting.web.security;

/**
 * Who is signed in. It is stored in the session as JSON, so keep it small and mappable, and no
 * password or name. Controllers get it with {@code @AuthenticationPrincipal SessionPrincipal},
 * which is null when no one is signed in.
 *
 * @param temporaryPassword whether a Property Manager still has to replace a temporary password. Always false for a Visitor.
 */
public record SessionPrincipal(String accountId, AccountRole role, boolean temporaryPassword) {
}
