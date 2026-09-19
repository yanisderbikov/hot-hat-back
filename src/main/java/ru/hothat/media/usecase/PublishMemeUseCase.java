package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.PublishMemeRequestDTO;
import ru.hothat.media.api.dto.PublishedMemeResponseDTO;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.domain.MemeAssetKind;
import ru.hothat.media.store.MemeFileStorage;
import ru.hothat.media.store.MemeStore;
import ru.hothat.util.Divisions;

import java.time.Clock;
import java.time.Instant;

/**
 * Опубликовать мем: файлы уже в хранилище, появляется карточка.
 *
 * <p>Заменяет прямую запись документа из браузера. Разница не в адресе, а в
 * том, кто заполняет поля. Автора ставит сервер по токену (A1) — раньше
 * {@code ownerUid} приезжал из тела, и правило доступа могло лишь сверить его
 * с вызывающим. Адреса файлов собираются по ключам, поэтому поля {@code src}
 * в заявке нет вовсе, а с ним исчезает возможность повесить на карточку
 * ссылку на чужой хост. Размер и тип ролика берутся у самого объекта: клиент
 * их больше не объявляет, а значит и не может ошибиться или соврать.
 *
 * <p>Порядок шагов: сперва спросить хранилище, потом писать. Карточка,
 * ссылающаяся на файл, который не доехал, показывает игроку чёрный экран в
 * момент выстрела.
 *
 * <p>Своей транзакции у сценария нет намеренно: он ходит в хранилище по сети,
 * а внешний вызов внутри транзакции держал бы соединение с базой открытым всё
 * время ожидания. Две записи — карточка и её файлы — обязаны лечь вместе, и
 * они лежат в одной транзакции внутри {@link MemeStore#publish}.
 */
@Service
@RequiredArgsConstructor
public class PublishMemeUseCase {

    private final MemeStore memes;
    private final MemeFileStorage files;
    private final MemeCardMapper cards;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    public PublishedMemeResponseDTO run(HotHatUser user, PublishMemeRequestDTO request) {
        files.requireConfigured();
        String memeId = request.memeId();
        if (MemeAssetKey.isBuiltin(memeId)) {
            // Встроенный мем заводит посев, а не игрок: переписать его нельзя.
            throw ApiException.of("BUILTIN_MEME_PROTECTED", 403);
        }
        String videoKey = MemeAssetKey.requireAssetOf(request.videoPath(), user.uid(), memeId, MemeAssetKind.VIDEO);
        String posterKey = request.posterPath() == null
                ? null
                : MemeAssetKey.requireAssetOf(request.posterPath(), user.uid(), memeId, MemeAssetKind.POSTER);

        long videoSize = files.sizeOf(videoKey)
                .orElseThrow(() -> ApiException.of("MEME_MEDIA_MISSING", 409));
        // Заставку у хранилища не спрашиваем: её отсутствие не ломает показ,
        // а лишний платный HEAD на каждую публикацию стоит денег.
        long posterSize = posterKey == null ? 0L : files.sizeOf(posterKey).orElse(0L);

        // Идентификатор мема случайный, поэтому попадание в занятый — не
        // совпадение, а попытка подменить чужой карточке ссылку на медиа.
        memes.find(memeId).ifPresent(existing -> requireOwn(user, existing));
        MemeStore.Card card = memes.publish(new MemeStore.Publication(
                memeId,
                user.uid(),
                request.title(),
                request.durationMs(),
                Divisions.normalize(request.divisionLanguage()),
                request.sourceUrl(),
                request.importMode(),
                videoKey,
                MemeAssetKey.mimeOf(videoKey, MemeAssetKind.VIDEO),
                videoSize,
                posterKey,
                posterKey == null ? null : MemeAssetKey.mimeOf(posterKey, MemeAssetKind.POSTER),
                posterSize), Instant.now(clock));
        return new PublishedMemeResponseDTO(cards.card(card, user.uid()));
    }

    private static void requireOwn(HotHatUser user, MemeStore.Card meme) {
        if (meme.builtin()) {
            throw ApiException.of("BUILTIN_MEME_PROTECTED", 403);
        }
        String owner = meme.ownerUid() == null ? "" : meme.ownerUid();
        if (!owner.isEmpty() && !owner.equals(user.uid())) {
            throw ApiException.of("NOT_MEME_AUTHOR", 403);
        }
    }
}
