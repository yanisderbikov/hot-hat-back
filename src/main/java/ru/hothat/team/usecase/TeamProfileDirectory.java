package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.profile.spi.PlayerCardDirectory;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.util.Divisions;

import java.util.Collection;
import java.util.Map;

/**
 * Карточки игроков, которых показывают экраны команды, — одним обращением на
 * весь список сразу.
 *
 * <p>Появился ради того же веера, что и справочники у друзей и переписки:
 * старый движок читал профиль на каждом участнике, и звали его четыре
 * сценария подряд — своя команда, лобби, старт префлайта и чтение префлайта.
 *
 * <p>Копии ников в самой команде здесь больше не запасной ответ: копия
 * устаревала при первой же смене имени, а карточка — нет.
 */
@Component
@RequiredArgsConstructor
public class TeamProfileDirectory {

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

        /** Дивизион; у пропавшей карточки — тот же запасной, что у нормализатора. */
        public DivisionLanguage divisionLanguage(String uid) {
            PlayerCardPort.Card card = byUid.get(uid);
            return card == null ? DivisionLanguage.RU
                    : DivisionLanguage.fromWire(Divisions.normalize(card.divisionLanguage()));
        }
    }
}
