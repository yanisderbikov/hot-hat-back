package ru.hothat.profile.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.util.Ids;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Реализация {@link PlayerCardPort}: живёт у владельца таблицы.
 *
 * <p>Заглушка имени объявлена здесь и только здесь. Раньше строка «Игрок»
 * была вписана в пять разных мест — в справочники друзей, переписки, команды,
 * рейтинга и в старый движок профиля, — и любая правка расходилась бы с
 * четырьмя оставшимися.
 */
@Component
@RequiredArgsConstructor
public class PlayerCardDirectory implements PlayerCardPort {

    /** Так игрока без карточки подписывали и раньше; менять нельзя — это видно. */
    public static final String PLACEHOLDER_NICKNAME = "Игрок";

    private final ProfileStore profiles;

    @Override
    public Optional<Card> card(String uid) {
        return profiles.card(uid).map(PlayerCardDirectory::view);
    }

    @Override
    public Map<String, Card> cards(Collection<String> uids) {
        Map<String, Card> result = new LinkedHashMap<>();
        profiles.cards(uids).forEach((uid, card) -> result.put(uid, view(card)));
        return result;
    }

    @Override
    public String nicknameOf(String uid) {
        return profiles.card(uid)
                .map(ProfileStore.Card::nickname)
                .filter(nickname -> nickname != null && !nickname.isBlank())
                .orElse(PLACEHOLDER_NICKNAME);
    }

    @Override
    public Optional<String> uidByNickname(String nickname) {
        return profiles.uidByNickname(nickname);
    }

    @Override
    public String requireUidByNickname(String nickname) {
        String clean = nickname == null ? "" : nickname.trim();
        if (!Ids.NICKNAME.matcher(clean).matches()) {
            throw ApiException.of("NICKNAME_INVALID", 400);
        }
        return profiles.uidByNickname(clean)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
    }

    @Override
    public boolean nicknameTaken(String nickname) {
        return profiles.nicknameTaken(nickname);
    }

    @Override
    public long onlineSince(Instant since) {
        return profiles.onlineSince(since);
    }

    private static Card view(ProfileStore.Card card) {
        return new Card(card.uid(), card.nickname(), card.avatarDataUrl(),
                card.divisionLanguage(), card.uiLanguage(), card.lastSeenAtMs(), card.activeRoomId());
    }
}
