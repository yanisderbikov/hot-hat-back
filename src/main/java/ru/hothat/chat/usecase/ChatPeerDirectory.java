package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.profile.spi.PlayerCardDirectory;
import ru.hothat.profile.spi.PlayerCardPort;

import java.util.Collection;
import java.util.Map;

/**
 * Карточки собеседников для списка переписок — одним обращением на весь список.
 *
 * <p>Появился ради того же веера, что и справочник у друзей: старый движок на
 * каждой связи читал профиль, причём в двух ветках цикла — при починке
 * потерянной шапки и снова после неё.
 *
 * <p>Свой, а не заимствованный у друзей: там имя из заявки идёт в дело, когда
 * карточки нет, а переписке эта подсказка не нужна — имя собеседника лежит
 * копией в самой шапке переписки, и решает о ней вызывающий.
 */
@Component
@RequiredArgsConstructor
public class ChatPeerDirectory {

    /** «Аватара нет» движок отдавал пустой строкой; форма ответа не меняется. */
    private static final String NO_AVATAR = "";

    private final PlayerCardPort cards;

    /** Читает карточки названных собеседников; пустой список в базу не идёт. */
    public Snapshot load(Collection<String> uids) {
        return new Snapshot(cards.cards(uids));
    }

    /** Прочитанные карточки. Запросов больше не делает — только карта в памяти. */
    public static final class Snapshot {

        private final Map<String, PlayerCardPort.Card> byUid;

        private Snapshot(Map<String, PlayerCardPort.Card> byUid) {
            this.byUid = byUid;
        }

        /** Имя собеседника; у пропавшей карточки — заглушка владельца. */
        public String nickname(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null || card.nickname() == null
                    ? PlayerCardDirectory.PLACEHOLDER_NICKNAME : card.nickname();
        }

        /** Аватар data-URL'ом; у пропавшей карточки — пустая строка, как у движка. */
        public String avatarDataUrl(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null || card.avatarDataUrl() == null ? NO_AVATAR : card.avatarDataUrl();
        }
    }
}
