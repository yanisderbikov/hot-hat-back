package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.media.api.dto.MemeCardView;
import ru.hothat.media.store.MemeStore;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.util.Divisions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Карточка мема из хранилища — в карточку для экрана.
 *
 * <p>Живёт здесь, а не в контроллере: перекладывание полей — это шаг
 * сценария, а не разбор запроса.
 *
 * <p>Имя автора берётся у области профиля на чтении, а не из копии в строке
 * мема: копия устаревала в тот же миг, когда игрок менял ник, и библиотека
 * подписывала ролик прежним именем до скончания века. Имена запрашиваются
 * пачкой на всю страницу — шестьдесят карточек стоят одного чтения.
 */
@Component
@RequiredArgsConstructor
public class MemeCardMapper {

    private final MemeFileUrls urls;
    private final PlayerCardPort playerCards;

    /** Одна карточка: имя автора спрашивается точечно. */
    public MemeCardView card(MemeStore.Card meme, String viewerUid) {
        return card(meme, viewerUid, names(List.of(meme)));
    }

    /** Страница карточек: имена авторов приезжают одним запросом на всю. */
    public List<MemeCardView> cards(Collection<MemeStore.Card> memes, String viewerUid) {
        Map<String, String> names = names(memes);
        List<MemeCardView> views = new ArrayList<>(memes.size());
        for (MemeStore.Card meme : memes) {
            views.add(card(meme, viewerUid, names));
        }
        return views;
    }

    private MemeCardView card(MemeStore.Card meme, String viewerUid, Map<String, String> names) {
        String ownerUid = meme.ownerUid() == null ? "" : meme.ownerUid();
        MemeStore.StoredFile video = meme.video();
        MemeStore.StoredFile poster = meme.poster();
        return new MemeCardView(
                meme.memeId(),
                meme.title() == null ? "" : meme.title(),
                meme.durationMs(),
                fileUrl(video),
                video == null ? null : video.storageKey(),
                fileUrl(poster),
                poster == null ? null : poster.storageKey(),
                video == null ? null : video.contentType(),
                video == null ? 0L : video.sizeBytes(),
                ownerUid,
                names.getOrDefault(ownerUid, ""),
                Divisions.normalize(meme.divisionLanguage()),
                meme.builtin(),
                meme.sourceUrl(),
                meme.importMode(),
                (meme.createdAt() == null ? Instant.EPOCH : meme.createdAt()).toEpochMilli(),
                !ownerUid.isEmpty() && ownerUid.equals(viewerUid));
    }

    /**
     * Адрес файла считается по ключу, а не хранится: подпись хранилища живёт
     * час, а карточка переживает и публикацию, и перезаливку ролика.
     * Пусто — у мема нет файла: такие строки остались от прежней модели, где
     * ролик лежал байтами прямо в базе.
     */
    private String fileUrl(MemeStore.StoredFile file) {
        return file == null ? null : urls.stable(file.storageKey());
    }

    private Map<String, String> names(Collection<MemeStore.Card> memes) {
        List<String> uids = new ArrayList<>(memes.size());
        for (MemeStore.Card meme : memes) {
            if (meme.ownerUid() != null && !meme.ownerUid().isBlank()) {
                uids.add(meme.ownerUid());
            }
        }
        if (uids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new java.util.LinkedHashMap<>();
        playerCards.cards(uids).forEach((uid, card) -> names.put(uid, card.nickname()));
        return names;
    }
}
