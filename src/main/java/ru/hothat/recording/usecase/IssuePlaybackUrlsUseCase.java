package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.RecordingPlaybackUrlsResponseDTO;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.domain.RetentionRules;
import ru.hothat.recording.port.RecordingStoragePort;
import ru.hothat.recording.store.RecordingStore;

import java.util.UUID;

/**
 * Ссылки на просмотр и скачивание записи.
 *
 * <p>Подписанная ссылка живёт полчаса. Проверки существования файла перед
 * выдачей нет намеренно: это лишние платные обращения к хранилищу, а
 * отсутствие файла всё равно проявится на самом запросе.
 *
 * <p>Файла может не быть вовсе — тогда и подписывать нечего. Раньше признаком
 * служила пустая колонка пути внутри строки записи; теперь это отсутствие
 * строки файла, у которой свой писатель и своё время появления.
 */
@Service
@RequiredArgsConstructor
public class IssuePlaybackUrlsUseCase {

    private final RecordingOwnershipGuard ownershipGuard;
    private final RecordingStoragePort storage;
    private final RecordingStore store;

    @PreAuthorize("hasRole('USER')")
    public RecordingPlaybackUrlsResponseDTO run(HotHatUser user, String recordingId) {
        UUID id = ownershipGuard.requireWatchable(user, recordingId);
        RecordingStore.Card card = store.card(id)
                .orElseThrow(() -> ApiException.of("RECORDING_NOT_FOUND", 404));
        RecordingStore.Artifact artifact = card.artifact();
        if (artifact == null || artifact.deletedAtMs() != null) {
            throw ApiException.of("RECORDING_NOT_READY", 409);
        }
        RecordingKey key = new RecordingKey(card.passport().roomId(), card.passport().gameNumber());
        RecordingStoragePort.PlaybackUrls urls =
                storage.presign(artifact.objectPath(), key.downloadName(), RetentionRules.PLAYBACK_URL_TTL);
        return new RecordingPlaybackUrlsResponseDTO(card.passport().recordingId(),
                urls.watchUrl(), urls.downloadUrl(), urls.expiresAtMs());
    }
}
