package ru.hothat.controller;

import org.springframework.security.core.Authentication;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;

/** Достаёт пользователя, положенного в контекст фильтром access-токена. */
final class CurrentUser {

    private CurrentUser() {
    }

    static HotHatUser of(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof HotHatUser user)) {
            throw ApiException.of("AUTH_REQUIRED", 401);
        }
        return user;
    }

    static HotHatUser admin(Authentication authentication) {
        HotHatUser user = of(authentication);
        if (!user.admin()) {
            throw ApiException.of("ADMIN_REQUIRED", 403);
        }
        return user;
    }
}
