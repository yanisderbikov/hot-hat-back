package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.AdminRecordingUrlsResponseDTO;
import ru.hothat.admin.port.RecordingArchivePort;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.HotHatUser;

/**
 * Выдать администратору ссылки на любую запись.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=admin_urls}.
 * Отдельный сценарий, а не флаг {@code admin} в общем: правило доступа
 * различается целиком. Игроку ссылку дают, если он запись сохранил или ею с
 * ним поделились; администратору — на любую, потому что он разбирает жалобу.
 * Пока это был один метод с булевым аргументом, оба правила жили в одном
 * {@code if} и менялись вместе.
 *
 * <p>Транзакции нет: сценарий ничего не пишет, а подписывание ссылок —
 * внешний вызов к хранилищу.
 */
@Service
@RequiredArgsConstructor
public class IssueAdminPlaybackUrlsUseCase {

    private final RecordingArchivePort recordings;

    @PreAuthorize("hasRole('ADMIN')")
    public AdminRecordingUrlsResponseDTO run(HotHatUser admin, String recordingId) {
        RecordingArchivePort.Archived recording = recordings.find(recordingId)
                .orElseThrow(ErrorCode.RECORDING_NOT_FOUND::raise);
        RecordingArchivePort.PlaybackUrls urls = recordings.signUrls(recording, downloadName(recording));
        return new AdminRecordingUrlsResponseDTO(
                recording.recordingId(), urls.watchUrl(), urls.downloadUrl(), urls.expiresAtMs());
    }

    /** Имя файла для «Скачать»: по нему администратор узнает запись в папке загрузок. */
    private static String downloadName(RecordingArchivePort.Archived recording) {
        String room = recording.roomId() == null || recording.roomId().isBlank()
                ? "game" : recording.roomId();
        return "HOT-HAT-" + room + "-" + recording.gameNumber() + ".mp4";
    }
}
