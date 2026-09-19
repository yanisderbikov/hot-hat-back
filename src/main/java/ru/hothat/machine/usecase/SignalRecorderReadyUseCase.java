package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RecorderReadySignalResponseDTO;
import ru.hothat.machine.api.dto.SignalRecorderReadyRequestDTO;
import ru.hothat.model.room.Room;
import ru.hothat.recording.spi.RecordingLifecyclePort;

/**
 * Рекордер подключился к комнате и готов снимать.
 *
 * <p>Раньше это был мутирующий GET ({@code ?ready=1}), теперь POST-сигнал; в
 * базе это по-прежнему отметка времени в строке СЕССИИ РЕКОРДЕРА — важно не
 * «сколько раз позвали», а «дошёл ли рекордер до этого шага».
 *
 * <p>Личность в LiveKit нужна экрану: по ней он прячет плитку рекордера из
 * сетки участников.
 */
@Service
@RequiredArgsConstructor
public class SignalRecorderReadyUseCase {

    private final RecorderSceneReader reader;
    private final RecordingLifecyclePort recordings;

    @PreAuthorize("hasRole('RECORDER')")
    public RecorderReadySignalResponseDTO run(String roomId, int gameNumber,
                                              SignalRecorderReadyRequestDTO request) {
        RecorderSceneReader.Snapshot snapshot = reader.load(roomId, gameNumber);
        reader.requireCurrent(snapshot);
        Room room = snapshot.room();
        String identity = identity(request);
        recordings.markRecorderReady(roomId, gameNumber, room.getPhase(), identity);
        return new RecorderReadySignalResponseDTO(roomId, gameNumber, room.getPhase(),
                identity.isEmpty() ? null : identity, System.currentTimeMillis());
    }

    private static String identity(SignalRecorderReadyRequestDTO request) {
        String value = request == null || request.livekitIdentity() == null ? "" : request.livekitIdentity();
        return value.trim();
    }
}
