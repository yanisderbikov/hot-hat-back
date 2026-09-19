package ru.hothat.service.storage;

import java.time.Duration;
import java.util.Optional;

/** S3-совместимое хранилище: мем-видео и MP4-записи партий. */
public interface ObjectStorageService {

    boolean isConfigured();

    String bucket();

    String endpoint();

    /** Подписанная ссылка на скачивание. */
    String presignGet(String key, Duration ttl, String contentType, String contentDisposition, String cacheControl);

    /** Подписанная ссылка на загрузку — клиент кладёт файл сам, минуя бекенд. */
    String presignPut(String key, Duration ttl, String contentType);

    void put(String key, byte[] body, String contentType);

    void delete(String key);

    /** Размер объекта; пусто, если объекта ещё нет. */
    Optional<Long> contentLength(String key);

    /** Объект бакета: ключ, размер и время изменения. */
    record StoredObject(String key, long size, java.time.Instant lastModified) {
    }

    /** Перечисление объектов по префиксу, не больше limit штук. */
    java.util.List<StoredObject> list(String prefix, int limit);
}
