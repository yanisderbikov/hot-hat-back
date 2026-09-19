package ru.hothat.recording.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.recording.domain.RetentionRules;
import ru.hothat.recording.port.RecordingStoragePort;

/**
 * Реализация {@link RecordingFilePort}: та же подпись, что и своим игрокам.
 *
 * <p>Отдельного пути к хранилищу для консоли нет намеренно: подписывает всем
 * один и тот же {@link RecordingStoragePort}, и отличается только то, кому
 * разрешено просить, — а это решает сама консоль.
 */
@Service
@RequiredArgsConstructor
public class RecordingFiles implements RecordingFilePort {

    private final RecordingStoragePort storage;

    @Override
    public PlaybackUrls presign(String objectPath, String downloadName) {
        if (objectPath == null || objectPath.isBlank()) {
            // Строка записи есть, файла нет: съёмка не дошла до выгрузки либо
            // файл уже убран. Подписывать нечего, и молчать об этом нельзя.
            throw ApiException.of("RECORDING_NOT_READY", 409);
        }
        RecordingStoragePort.PlaybackUrls urls =
                storage.presign(objectPath, downloadName, RetentionRules.PLAYBACK_URL_TTL);
        return new PlaybackUrls(urls.watchUrl(), urls.downloadUrl(), urls.expiresAtMs());
    }
}
