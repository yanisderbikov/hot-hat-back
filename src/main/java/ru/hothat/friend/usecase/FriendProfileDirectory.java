package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.profile.spi.PlayerCardDirectory;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.util.Divisions;

import java.util.Collection;
import java.util.Map;

/**
 * Карточки игроков, которых показывает экран друзей, — одним обращением на
 * весь список сразу.
 *
 * <p>Появился ради того, чтобы убрать веер: раньше каждое чтение дружбы шло
 * через старый движок, а тот на каждой строке цикла читал профиль и следом
 * разрешал ник, читая тот же профиль второй раз — сотня друзей стоила две
 * сотни обращений к базе.
 *
 * <p>Лестницы разрешения ника больше нет вовсе, и это главное изменение.
 * Она существовала потому, что имя лежало в трёх местах — {@code nickname},
 * {@code display_name} и таблица индекса ников, — и любое из них могло
 * оказаться пустым. В {@code v2.player_profile} имя одно и обязательное;
 * остался только случай «карточки нет», и на него отвечает заглушка,
 * объявленная у владельца.
 */
@Component
@RequiredArgsConstructor
public class FriendProfileDirectory {

    /** Запасной дивизион: тот же, что подставляет нормализатор. */
    private static final String DEFAULT_DIVISION = Divisions.normalize(null);

    private final PlayerCardPort cards;

    /** Читает карточки названных игроков; пустой список в базу не идёт. */
    public Snapshot load(Collection<String> uids) {
        return new Snapshot(cards.cards(uids));
    }

    /** Прочитанные карточки. Запросов больше не делает — только карта в памяти. */
    public static final class Snapshot {

        private final Map<String, PlayerCardPort.Card> byUid;

        private Snapshot(Map<String, PlayerCardPort.Card> byUid) {
            this.byUid = byUid;
        }

        /**
         * Ник для показа.
         *
         * @param stored имя, записанное когда-то в заявке. Идёт в дело только
         *               когда карточки нет вовсе: копия могла устареть, но
         *               строка списка без имени бесполезна совсем
         */
        public String nickname(String uid, String stored) {
            PlayerCardPort.Card card = byUid.get(uid);
            if (card != null && card.nickname() != null) {
                return card.nickname();
            }
            String hint = stored == null ? "" : stored.trim();
            return hint.isEmpty() || PlayerCardDirectory.PLACEHOLDER_NICKNAME.equals(hint)
                    ? PlayerCardDirectory.PLACEHOLDER_NICKNAME : hint;
        }

        /** Аватар как data-URL; null — аватара нет или карточки не нашлось. */
        public String avatarDataUrl(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null ? null : card.avatarDataUrl();
        }

        /** Когда игрока видели в последний раз; 0 — не видели ни разу. */
        public long lastSeenAtMs(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null ? 0L : card.lastSeenAtMs();
        }

        /** Дивизион; у пропавшей карточки — тот же запасной, что у нормализатора. */
        public String divisionLanguage(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null ? DEFAULT_DIVISION : Divisions.normalize(card.divisionLanguage());
        }
    }
}
