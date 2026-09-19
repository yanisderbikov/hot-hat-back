package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RecorderSceneResponseDTO;
import ru.hothat.machine.api.dto.RecorderSceneState;
import ru.hothat.model.room.Room;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.repository.GetterRoom;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;

import java.util.List;

/**
 * Отдаёт кадр партии странице записи.
 *
 * <p>Самый горячий адрес машинной поверхности: страница опрашивает его четыре
 * раза в секунду ({@code recording-view.js:37}). Поэтому здесь нет ни зеркала
 * комнаты, ни арсеналов — только то, из чего рисуется кадр.
 */
@Service
@RequiredArgsConstructor
public class ReadRecorderSceneUseCase {

    private final RecorderSceneReader reader;
    private final RecorderSceneAssembler assembler;
    private final GetterRoom getterRoom;

    @PreAuthorize("hasRole('RECORDER')")
    public RecorderSceneResponseDTO run(String roomId, int gameNumber) {
        RecorderSceneReader.Snapshot snapshot = reader.load(roomId, gameNumber);
        if (!snapshot.current()) {
            return stale(roomId, gameNumber);
        }
        Room room = snapshot.room();
        return new RecorderSceneResponseDTO(
                RecorderSceneState.LIVE,
                roomId,
                gameNumber,
                room.getPhase(),
                Json.str(room.getName() == null ? roomId : room.getName(), 120),
                GameMode.fromWire(room.getGameMode()),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getIsPrivate()),
                DivisionLanguage.fromWire(Divisions.normalize(room.getDivisionLanguage())),
                DivisionLanguage.fromWire(Divisions.normalize(room.getGameLanguage())),
                blankToNull(room.getCurrentTeamId()),
                room.getCurrentTeamIndex() == null ? 0 : room.getCurrentTeamIndex(),
                room.getTeamOrder() == null ? List.of() : List.copyOf(room.getTeamOrder()),
                room.getWordsLeft() == null ? 0 : Math.max(0, room.getWordsLeft()),
                room.getCurrentTurnScore() == null ? 0 : Math.max(0, room.getCurrentTurnScore()),
                blankToNull(Json.str(room.getCurrentWord(), 180)),
                blankToNull(Json.str(room.getLastGuessedWord(), 120)),
                blankToNull(Json.str(room.getLastActionType(), 40)),
                blankToNull(Json.str(room.getLastActionWord() == null
                        ? room.getLastGuessedWord() : room.getLastActionWord(), 120)),
                room.getLastActionAtMs() == null ? 0L : room.getLastActionAtMs(),
                room.getTurnEndsAt() == null ? 0L : room.getTurnEndsAt(),
                room.getAppealEndsAt() == null ? 0L : room.getAppealEndsAt(),
                Boolean.TRUE.equals(room.getGamePaused()),
                blankToNull(room.getExplainerUid()),
                blankToNull(room.getGuesserUid()),
                assembler.sabotageEvent(room.getSabotageEvent(), gameNumber),
                assembler.recentSabotage(room, gameNumber),
                assembler.teams(getterRoom.getTeams(roomId)),
                assembler.scenePlayers(getterRoom.getPlayers(roomId)),
                System.currentTimeMillis());
    }

    /**
     * Комната ушла к следующей партии. Фаза называется finished намеренно:
     * страница по ней сворачивает съёмку, и это поведение сохранено.
     */
    private RecorderSceneResponseDTO stale(String roomId, int gameNumber) {
        return new RecorderSceneResponseDTO(
                RecorderSceneState.STALE_GAME, roomId, gameNumber, "finished",
                null, null, null, null, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, List.of(), List.of(), List.of(), System.currentTimeMillis());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
