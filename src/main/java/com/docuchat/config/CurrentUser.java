package com.docuchat.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** NFR-SEC-01: tenant id always comes from the validated JWT `sub` claim, never from the request body. */
public final class CurrentUser {
    private CurrentUser() {}

    public static String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        return auth.getName();
    }
}
