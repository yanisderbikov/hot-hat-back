package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RecorderSceneState;
import ru.hothat.machine.api.dto.RecorderWordResponseDTO;
import ru.hothat.model.room.Room;
import ru.hothat.util.Json;

/**
 * Отдаёт слово текущего хода.
 *
 * <p>Прогрев здесь не принимается: до старта партии слова ещё нет, и отвечать
 * на этот вопрос нечем. Так же ведёт себя и сегодняшний режим {@code word=1} —
 * он единственный сверяет номер партии точно.
 */
@Service
@RequiredArgsConstructor
public class ReadRecorderWordUseCase {

    private final RecorderSceneReader reader;

    @PreAuthorize("hasRole('RECORDER')")
    public RecorderWordResponseDTO run(String roomId, int gameNumber) {
        RecorderSceneReader.Snapshot snapshot = reader.load(roomId, gameNumber);
        if (!snapshot.exact()) {
            return new RecorderWordResponseDTO(RecorderSceneState.STALE_GAME, roomId, gameNumber,
                    "finished", null, null, null, null, System.currentTimeMillis());
        }
        Room room = snapshot.room();
        reader.requireRecording(room);
        return new RecorderWordResponseDTO(
                RecorderSceneState.LIVE,
                roomId,
                snapshot.roomGameNumber(),
                room.getPhase(),
                blankToNull(room.getTurnId()),
                blankToNull(Json.str(room.getCurrentWord(), 180)),
                room.getWordsLeft() == null ? 0 : Math.max(0, room.getWordsLeft()),
                room.getLastActionAtMs() == null ? 0L : room.getLastActionAtMs(),
                System.currentTimeMillis());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
