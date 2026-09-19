package ru.hothat.media.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.common.storage.ObjectStore;
import ru.hothat.config.ApiException;
import ru.hothat.media.domain.MemeAssetKey;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Объекты мемов в хранилище: подписи, размеры и уборка.
 *
 * <p>Дверь области в S3, как {@link MemeStore} — дверь в базу. Сроки подписей
 * объявлены здесь и только здесь: раньше те же три числа стояли в переходном
 * движке, и любой новый вызов рисковал подписать ссылку на своё усмотрение.
 *
 * <p>Само хранилище остаётся общим ({@link ObjectStore}): оно
 * инфраструктура, а не таблица, и записями партий пользуется вторая область.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemeFileStorage {

    /** Билет на загрузку: браузер кладёт файл сам, минуя бекенд. */
    public static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    /** Билет на воспроизведение: два часа, чтобы браузер переиспользовал кеш и range-запросы. */
    public static final Duration PLAYBACK_TTL = Duration.ofHours(2);
    /** Редирект постоянного адреса: подпись живёт ровно до следующего запроса. */
    private static final Duration REDIRECT_TTL = Duration.ofHours(1);

    /** Кеш браузера у подписанной ссылки — приватный: подпись чужой не годится. */
    private static final String PLAYBACK_CACHE = "private, max-age=7200";

    private final ObjectStore storage;

    /** Хранилище настроено; иначе ни билета, ни публикации быть не может. */
    public void requireConfigured() {
        if (!storage.configured()) {
            throw ApiException.of("S3_MEDIA_STORAGE_NOT_CONFIGURED", 503);
        }
    }

    public boolean configured() {
        return storage.configured();
    }

    public String presignUpload(String storageKey, String contentType) {
        requireConfigured();
        return storage.presignPut(storageKey, UPLOAD_TTL, contentType);
    }

    public String presignPlayback(String storageKey) {
        requireConfigured();
        return storage.presignGet(storageKey, PLAYBACK_TTL, null, null, PLAYBACK_CACHE);
    }

    public String presignRedirect(String storageKey) {
        requireConfigured();
        return storage.presignGet(storageKey, REDIRECT_TTL, null, null, null);
    }

    /** Размер объекта; пусто — объекта в бакете нет. */
    public Optional<Long> sizeOf(String storageKey) {
        requireConfigured();
        return storage.contentLength(storageKey);
    }

    public void put(String storageKey, byte[] body, String contentType) {
        requireConfigured();
        storage.put(storageKey, body, contentType);
    }

    public void delete(String storageKey) {
        requireConfigured();
        storage.delete(storageKey);
    }

    /**
     * Убрать объекты снятого мема.
     *
     * <p>Неудача уборки не отменяет снятие: карточки уже нет, а оплаченный, но
     * никому не нужный объект — меньшее зло, чем мем, который игрок «удалил»,
     * а он остался в библиотеке. Осиротевший файл найдёт сверка с бакетом.
     *
     * <p>Ключ проверяется по форме ещё раз, хотя он прочитан из своей же
     * строки: {@code delete} по произвольной строке из базы — ровно та
     * операция, которую стоит защищать от опечатки в миграции.
     */
    public void forget(Iterable<String> storageKeys) {
        if (!storage.configured()) {
            return;
        }
        for (String key : storageKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                storage.delete(MemeAssetKey.require(key));
            } catch (RuntimeException e) {
                log.warn("Файл мема {} не удалён: {}", key, e.getMessage());
            }
        }
    }

    /** Перечисление объектов бакета для сверки с библиотекой. */
    public List<StoredFile> list(String prefix, int limit) {
        requireConfigured();
        return storage.list(prefix, limit).stream()
                .map(object -> new StoredFile(object.key(), object.size(), object.lastModified()))
                .toList();
    }

    /**
     * Объект бакета глазами области.
     *
     * <p>Своя запись, а не запись хранилища: перечисление объектов уходит в
     * {@code spi}, к сверке библиотеки, и тип общей инфраструктуры не должен
     * выходить за эту дверь. Полей столько же — уборке больше не нужно.
     */
    public record StoredFile(String key, long size, Instant lastModified) {
    }
}
