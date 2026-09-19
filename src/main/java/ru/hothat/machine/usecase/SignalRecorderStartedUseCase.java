package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RecorderStartSignalResponseDTO;
import ru.hothat.machine.api.dto.SignalRecorderStartedRequestDTO;
import ru.hothat.model.room.Room;
import ru.hothat.recording.spi.RecordingLifecyclePort;

/**
 * Рекордер сообщил, что пошла запись.
 *
 * <p>Второй из трёх шагов страницы-рекордера; порядок шагов проверяет сама
 * база ({@code ck_recorder_session_order}), поэтому «снимаю, но не готов» в
 * таблицу не попадёт.
 */
@Service
@RequiredArgsConstructor
public class SignalRecorderStartedUseCase {

    private final RecorderSceneReader reader;
    private final RecordingLifecyclePort recordings;

    @PreAuthorize("hasRole('RECORDER')")
    public RecorderStartSignalResponseDTO run(String roomId, int gameNumber,
                                              SignalRecorderStartedRequestDTO request) {
        RecorderSceneReader.Snapshot snapshot = reader.load(roomId, gameNumber);
        reader.requireExact(snapshot);
        Room room = snapshot.room();
        String identity = request == null || request.livekitIdentity() == null
                ? "" : request.livekitIdentity().trim();
        recordings.markRecorderStarted(roomId, gameNumber, room.getPhase(), identity);
        return new RecorderStartSignalResponseDTO(roomId, gameNumber, room.getPhase(),
                identity.isEmpty() ? null : identity, System.currentTimeMillis());
    }
}
