package ru.hothat.conference.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.common.storage.ObjectStore;
import ru.hothat.config.ApiException;

import java.time.Duration;

/**
 * Вложения чата видео-чата в хранилище объектов.
 *
 * <p>Дверь области в S3, как {@link ConferenceStore} — дверь в базу. Сроки
 * подписей объявлены здесь и только здесь. Само хранилище общее
 * ({@link ObjectStore}): оно инфраструктура, а не таблица.
 *
 * <p>Ссылка на просмотр подписывается на каждом чтении ленты, а не хранится:
 * вложения видны только участникам, и постоянного открытого адреса у них
 * нет намеренно — в отличие от общей библиотеки мемов.
 */
@Component
@RequiredArgsConstructor
public class ConferenceFileStorage {

    /** Билет на загрузку: браузер кладёт файл сам, минуя бекенд. */
    public static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    /**
     * Ссылка на просмотр: два часа. Кадр канала приносит свежие ссылки при
     * каждом изменении ленты, а дольше двух часов тихая вкладка созвона не
     * живёт — токен видеосвязи тоже переспрашивают.
     */
    public static final Duration VIEW_TTL = Duration.ofHours(2);

    private static final String VIEW_CACHE = "private, max-age=7200";

    private final ObjectStore storage;

    public void requireConfigured() {
        if (!storage.configured()) {
            throw ApiException.of("S3_MEDIA_STORAGE_NOT_CONFIGURED", 503);
        }
    }

    public String presignUpload(String storageKey, String contentType) {
        requireConfigured();
        return storage.presignPut(storageKey, UPLOAD_TTL, contentType);
    }

    /** Подписанная ссылка на просмотр; хранилище не настроено — пусто, лента жива без вложений. */
    public String presignView(String storageKey) {
        if (!storage.configured()) {
            return "";
        }
        return storage.presignGet(storageKey, VIEW_TTL, null, null, VIEW_CACHE);
    }
}
