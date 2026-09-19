package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RecorderRoomStateResponseDTO;
import ru.hothat.machine.api.dto.RecorderSceneState;
import ru.hothat.model.room.Room;

/**
 * Отдаёт рекордеру полное зеркало комнаты.
 *
 * <p>Запасной путь: страница берёт его, только если живое зеркало по сокету не
 * поднялось ({@code app-core.js:14083}). При нормальной работе этот адрес не
 * зовётся ни разу за партию, поэтому тяжесть ответа здесь допустима — в отличие
 * от сцены, которую опрашивают четыре раза в секунду.
 */
@Service
@RequiredArgsConstructor
public class ReadRecorderRoomStateUseCase {

    private final RecorderSceneReader reader;
    private final RecorderSceneAssembler assembler;

    @PreAuthorize("hasRole('RECORDER')")
    public RecorderRoomStateResponseDTO run(String roomId, int gameNumber) {
        RecorderSceneReader.Snapshot snapshot = reader.load(roomId, gameNumber);
        if (!snapshot.current()) {
            return new RecorderRoomStateResponseDTO(RecorderSceneState.STALE_GAME, roomId, gameNumber,
                    "finished", null, System.currentTimeMillis());
        }
        Room room = snapshot.room();
        reader.requireRecording(room);
        return new RecorderRoomStateResponseDTO(RecorderSceneState.LIVE, roomId, snapshot.roomGameNumber(),
                room.getPhase(), assembler.roomState(room, gameNumber), System.currentTimeMillis());
    }
}
