package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;

/**
 * Читает комнату съёмки и говорит, та ли это партия.
 *
 * <p>Сверка номера партии повторяется во всех семи режимах рекордера и в
 * каждом написана заново ({@code RecorderStateServiceImpl.handle}); из-за
 * этого одинаковые по смыслу ветки ведут себя по-разному — где-то расхождение
 * даёт 409, а где-то ответ {@code staleGame} с кодом 200. Здесь правило одно,
 * а как поступить с расхождением, решает уже сценарий: чтению достаточно
 * сказать «сцена устарела», а сигналу — нет, он обязан отказать.
 */
@Component
@RequiredArgsConstructor
public class RecorderSceneReader {

    private final GetterRoom getterRoom;

    /**
     * @param exact номер партии совпал с текущим номером комнаты
     * @param prewarm комната ещё в setup, а рекордер греется под следующую партию:
     *                прогрев начинается до старта, поэтому номер на единицу больше
     */
    public record Snapshot(Room room, int roomGameNumber, boolean exact, boolean prewarm) {

        /** Снимаем ту самую партию — точно или в прогреве. */
        public boolean current() {
            return exact || prewarm;
        }
    }

    public Snapshot load(String roomId, int gameNumber) {
        Room room = getterRoom.getById(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        int roomGameNumber = Math.max(0, room.getGameNumber() == null ? 0 : room.getGameNumber());
        boolean exact = gameNumber == roomGameNumber;
        boolean prewarm = "setup".equals(room.getPhase()) && gameNumber == roomGameNumber + 1;
        return new Snapshot(room, roomGameNumber, exact, prewarm);
    }

    /** Запись в комнате выключена — снимать нечего и незачем. */
    public void requireRecording(Room room) {
        if (!Boolean.TRUE.equals(room.getRecordGame())) {
            throw ApiException.of("RECORDING_DISABLED", 409);
        }
    }

    /** Сигналы рекордера не имеют смысла для чужой партии. */
    public void requireCurrent(Snapshot snapshot) {
        if (!snapshot.current()) {
            throw ApiException.of("RECORDING_GAME_NUMBER_MISMATCH", 409);
        }
    }

    /** Начало съёмки и завершение относятся только к идущей партии, прогрев не считается. */
    public void requireExact(Snapshot snapshot) {
        if (!snapshot.exact()) {
            throw ApiException.of("RECORDING_GAME_NUMBER_MISMATCH", 409);
        }
    }
}
