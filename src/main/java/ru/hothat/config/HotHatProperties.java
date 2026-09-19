package ru.hothat.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Переменные окружения Vercel-функций в одном месте. */
@Getter
@Component
public class HotHatProperties {

    @Value("${hot-hat.owner-email}")
    private String ownerEmail;

    @Value("${hot-hat.admin-emails:}")
    private String adminEmailsRaw;

    @Value("${hot-hat.admin-uids:}")
    private String adminUidsRaw;

    @Value("${hot-hat.public-url}")
    private String publicUrl;

    @Value("${hot-hat.api-public-url}")
    private String apiPublicUrl;

    @Value("${hot-hat.cron-secret:}")
    private String cronSecret;

    /**
     * Фолбэк намеренно не является e-mail: если переменная не задана, проверки
     * владельца не совпадут ни с одним аккаунтом.
     */
    public String owner() {
        return ownerEmail == null || ownerEmail.isBlank() ? "owner-not-configured" : ownerEmail.trim().toLowerCase();
    }

    public boolean isOwnerEmail(String email) {
        return email != null && email.trim().toLowerCase().equals(owner());
    }

    public Set<String> adminEmails() {
        return split(adminEmailsRaw).stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    public Set<String> adminUids() {
        return split(adminUidsRaw);
    }

    public String publicSiteUrl() {
        return trimSlash(publicUrl);
    }

    public String apiOrigin() {
        return trimSlash(apiPublicUrl);
    }

    private static String trimSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static Set<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
