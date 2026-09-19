package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.domain.MemeAssetKind;
import ru.hothat.media.store.MemeFileStorage;
import ru.hothat.media.store.MemeStore;
import ru.hothat.util.Divisions;

import java.time.Clock;
import java.time.Instant;

/**
 * Общая часть двух билетов на загрузку: ролика и заставки.
 *
 * <p>Сценариев два, потому что заставка необязательна и её неудача не должна
 * отменять публикацию. Но собирают они одно и то же: путь объекта, подпись
 * ссылки и черновик карточки с файлом в состоянии «билет выдан». Держать это
 * дважды значило бы позволить двум адресам разойтись в форме пути — а по нему
 * потом читается владение.
 */
@Component
@RequiredArgsConstructor
public class MemeUploadTickets {

    private final MemeStore memes;
    private final MemeFileStorage files;
    private final Clock clock;

    /** Билет: подписанная ссылка, ключ объекта и срок. */
    public record Ticket(String uploadUrl, String storageKey, long expiresAtMs) {
    }

    public Ticket issue(String uid, String memeId, MemeAssetKind kind,
                        String contentType, long sizeBytes, String divisionLanguage) {
        files.requireConfigured();
        String extension = kind.extensionOfMime(contentType);
        if (extension == null) {
            // Второй рубеж: форматы уже проверены на DTO, но подпись ссылки
            // считается по этому же типу, и молча подставить свой нельзя.
            throw ApiException.of(kind == MemeAssetKind.POSTER
                    ? "MEME_POSTER_MIME_INVALID" : "MEME_MIME_INVALID");
        }
        String division = Divisions.normalize(divisionLanguage);
        String storageKey = MemeAssetKey.of(division, uid, memeId, kind, extension);
        String uploadUrl = files.presignUpload(storageKey, contentType);
        Instant expiresAt = Instant.now(clock).plus(MemeFileStorage.UPLOAD_TTL);

        // Черновик карточки и есть билет (§6.3): отдельной таблицы билетов
        // нет, потому что файл без карточки и карточка без файла одинаково
        // бессмысленны. Публикация превратит черновик в выложенный мем, а
        // невыкупленный билет подметёт сторож просроченных.
        memes.reserveUpload(memeId, uid, division, kind, storageKey, contentType, sizeBytes, expiresAt);
        return new Ticket(uploadUrl, storageKey, expiresAt.toEpochMilli());
    }
}
