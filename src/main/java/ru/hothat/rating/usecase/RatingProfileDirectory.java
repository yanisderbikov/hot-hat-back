package ru.hothat.rating.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.profile.spi.PlayerCardDirectory;
import ru.hothat.profile.spi.PlayerCardPort;

import java.util.Collection;
import java.util.Map;

/**
 * Ники и аватары игроков, которых показывают таблицы сезона, — одним
 * обращением на всю страницу сразу.
 *
 * <p>Причина глубже, чем веер: раньше ник и аватар лежали копиями в самой
 * строке рейтинга и обновлялись только при зачёте следующей партии — игрок
 * менял ник, а таблица сезона показывала прежний до конца сезона. В v2 чужих
 * полей в таблицах сезона нет, и проекция собирается на чтении.
 */
@Component
@RequiredArgsConstructor
public class RatingProfileDirectory {

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

        /** Ник для показа; у пропавшей карточки — заглушка владельца. */
        public String nickname(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null || card.nickname() == null
                    ? PlayerCardDirectory.PLACEHOLDER_NICKNAME : card.nickname();
        }

        /** Аватар как data-URL; null — аватара нет или карточки не нашлось. */
        public String avatarDataUrl(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null ? null : card.avatarDataUrl();
        }
    }
}
