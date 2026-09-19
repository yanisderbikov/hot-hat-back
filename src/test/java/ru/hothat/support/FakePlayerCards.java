package ru.hothat.support;

import ru.hothat.profile.spi.PlayerCardPort;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Карточки игроков в памяти: правилам доступа нужен из них только дивизион. */
public final class FakePlayerCards implements PlayerCardPort {

    private final Map<String, Card> cards = new HashMap<>();

    public static FakePlayerCards empty() {
        return new FakePlayerCards();
    }

    public FakePlayerCards card(String uid, String divisionLanguage) {
        cards.put(uid, new Card(uid, uid, null, divisionLanguage, divisionLanguage, 0L, null));
        return this;
    }

    @Override
    public Optional<Card> card(String uid) {
        return Optional.ofNullable(cards.get(uid));
    }

    @Override
    public Map<String, Card> cards(Collection<String> uids) {
        Map<String, Card> found = new LinkedHashMap<>();
        for (String uid : uids) {
            Card card = cards.get(uid);
            if (card != null) {
                found.put(uid, card);
            }
        }
        return found;
    }

    @Override
    public String nicknameOf(String uid) {
        return card(uid).map(Card::nickname).orElse("Игрок");
    }

    @Override
    public Optional<String> uidByNickname(String nickname) {
        return cards.values().stream()
                .filter(card -> card.nickname().equals(nickname))
                .map(Card::uid)
                .findFirst();
    }

    @Override
    public String requireUidByNickname(String nickname) {
        return uidByNickname(nickname)
                .orElseThrow(() -> ru.hothat.config.ApiException.of("PLAYER_NOT_FOUND", 404));
    }

    @Override
    public boolean nicknameTaken(String nickname) {
        return uidByNickname(nickname).isPresent();
    }

    @Override
    public long onlineSince(Instant since) {
        return 0;
    }
}
