package ru.hothat.profile.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.util.Divisions;

/** Реализация {@link ProfileCommandPort}: пишет владелец таблицы. */
@Component
@RequiredArgsConstructor
public class ProfileCommands implements ProfileCommandPort {

    private final ProfileStore profiles;

    @Override
    public void createCard(String uid, String nickname, String divisionLanguage) {
        profiles.createCard(uid, nickname, Divisions.normalize(divisionLanguage));
    }

    @Override
    public String freeNicknameFrom(String hint, String uid) {
        return profiles.freeNicknameFrom(hint, uid);
    }

    @Override
    public void ensureCard(String uid, String nameHint) {
        profiles.ensureCard(uid, nameHint, Divisions.normalize(null));
    }
}
