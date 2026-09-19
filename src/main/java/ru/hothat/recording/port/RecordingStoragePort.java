package ru.hothat.recording.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Бакет с готовыми файлами записей.
 *
 * <p>Обращения к хранилищу — тоже сеть, и они тоже обязаны быть снаружи
 * транзакции. Порт держит эту границу и заодно прячет от сценариев различия
 * настроек S3.
 */
public interface RecordingStoragePort {

    boolean configured();

    String bucket();

    /** Размер объекта; пусто — файла в бакете нет. */
    Optional<Long> contentLength(String objectPath);

    /** Убрать файл. Отказ не бросает: не убранный файл — беда уборки, а не игрока. */
    boolean delete(String objectPath);

    /** Подписанные ссылки на просмотр и на скачивание. */
    PlaybackUrls presign(String objectPath, String downloadName, Duration ttl);

    record PlaybackUrls(String watchUrl, String downloadUrl, long expiresAtMs) {
    }
}
