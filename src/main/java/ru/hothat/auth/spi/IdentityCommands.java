package ru.hothat.auth.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.auth.store.IdentityStore;

/** Реализация {@link IdentityCommandPort}: пишет владелец таблицы. */
@Component
@RequiredArgsConstructor
public class IdentityCommands implements IdentityCommandPort {

    private final IdentityStore identities;

    @Override
    public void revokeAccess(String uid, String reason) {
        identities.revokeAccess(uid, reason == null || reason.isBlank() ? REASON_BAN : reason);
    }
}
