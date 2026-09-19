package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.machine.api.dto.RecorderSessionResponseDTO;
import ru.hothat.model.room.Room;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.repository.GetterRoom;
import ru.hothat.auth.spi.AccessTokenPort;
import ru.hothat.util.Divisions;

/**
 * Снаряжает рекордер перед съёмкой партии.
 *
 * <p>Заменяет режим {@code GET /api/recording-state?bootstrap=1}. Метод POST,
 * а не GET, потому что снаряжение выдаёт токен — это создание сессии съёмки, а
 * не чтение.
 *
 * <p>Расхождение номера партии здесь — отказ 409, а не «ответ про устаревшую
 * сцену», как сегодня. Причина в том, что старый ответ {@code staleGame} не
 * содержал токена, и страница всё равно падала на его отсутствии
 * ({@code app-core.js:13979}); 409 говорит то же самое, но честно.
 */
@Service
@RequiredArgsConstructor
public class OpenRecorderSessionUseCase {

    /**
     * Служебная личность рекордера. Строки в учётках за ней нет, поэтому
     * обычный фильтр такой токен не примет: он нужен только чтобы страница
     * открыла комнату. Поколение токенов нулевое, почты нет, гостем она не
     * считается — банить и менять пароль тут нечему.
     */
    private static final String RECORDER_SUBJECT = "hot-hat-recorder";

    private final RecorderSceneReader reader;
    private final RecorderSceneAssembler assembler;
    private final GetterRoom getterRoom;
    private final AccessTokenPort accessTokens;

    @PreAuthorize("hasRole('RECORDER_BOOTSTRAP')")
    public RecorderSessionResponseDTO run(String roomId, int gameNumber) {
        RecorderSceneReader.Snapshot snapshot = reader.load(roomId, gameNumber);
        reader.requireCurrent(snapshot);
        Room room = snapshot.room();
        reader.requireRecording(room);
        String language = Divisions.normalize(
                room.getGameLanguage() == null ? room.getDivisionLanguage() : room.getGameLanguage());
        return new RecorderSessionResponseDTO(
                accessTokens.createAccessToken(RECORDER_SUBJECT, null, 0, false),
                snapshot.prewarm(),
                roomId,
                gameNumber,
                room.getPhase(),
                DivisionLanguage.fromWire(language),
                assembler.roomState(room, gameNumber),
                assembler.teams(getterRoom.getTeams(roomId)),
                assembler.rosterPlayers(getterRoom.getPlayers(roomId)),
                System.currentTimeMillis());
    }
}
