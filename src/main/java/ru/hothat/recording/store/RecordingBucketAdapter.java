package ru.hothat.recording.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.common.storage.ObjectStore;
import ru.hothat.recording.port.RecordingStoragePort;

import java.time.Duration;
import java.util.Optional;

/**
 * Бакет записей поверх сегодняшнего клиента S3.
 *
 * <p>Подписанная ссылка живёт полчаса. Проверки существования файла перед
 * выдачей намеренно нет: это лишние платные обращения к хранилищу, а
 * отсутствие файла всё равно проявится на самом запросе.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingBucketAdapter implements RecordingStoragePort {

    private final ObjectStore storage;

    @Override
    public boolean configured() {
        return storage.configured();
    }

    @Override
    public String bucket() {
        return storage.bucket();
    }

    /** Настройки выгрузки Egress собирает адаптер LiveKit, а не сценарий. */
    String endpoint() {
        return storage.endpoint();
    }

    @Override
    public Optional<Long> contentLength(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return Optional.empty();
        }
        try {
            return storage.contentLength(objectPath);
        } catch (RuntimeException e) {
            log.warn("Размер объекта {} недоступен: {}", objectPath, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return false;
        }
        try {
            storage.delete(objectPath);
            return true;
        } catch (RuntimeException e) {
            // Не убранный файл — беда уборки, а не игрока: прогон обязан
            // дойти до конца и честно записать, что этот объект не сдался.
            log.warn("Не удалось удалить объект записи {}: {}", objectPath, e.getMessage());
            return false;
        }
    }

    @Override
    public PlaybackUrls presign(String objectPath, String downloadName, Duration ttl) {
        String cleanName = (downloadName == null ? "recording.mp4" : downloadName).replaceAll("[\"\\\\]", "_");
        String watchUrl = storage.presignGet(objectPath, ttl, "video/mp4", null, null);
        String downloadUrl = storage.presignGet(objectPath, ttl, "video/mp4",
                "attachment; filename=\"" + cleanName + "\"", null);
        return new PlaybackUrls(watchUrl, downloadUrl, System.currentTimeMillis() + ttl.toMillis());
    }
}
