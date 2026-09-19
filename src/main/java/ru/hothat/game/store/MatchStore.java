package ru.hothat.game.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.game.domain.MatchState;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;

/**
 * Единственная дверь партии в базу.
 *
 * <p>Партия сегодня лежит полями строки {@code room} и её jsonb-колонками —
 * так их оставила прежняя модель документов. Схема плана (§6.2) разводит её по
 * своим таблицам {@code match}, {@code match_player}, {@code match_word}, и
 * переезд затронет только этот класс и {@link MatchMapper}: движок и сценарии
 * состояния хранения не видят вовсе.
 */
@Component
@RequiredArgsConstructor
public class MatchStore {

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final MatchTeamScoreRepo teamScoreRepo;
    private final MatchRoomRepo rooms;

    /**
     * Открыть партию на запись.
     *
     * <p>Строка комнаты удерживается до конца транзакции: два быстрых нажатия
     * иначе прочитали бы одно и то же слово и записали за него два очка.
     * Вызывать только из сценария с транзакцией.
     */
    public MatchSession open(String roomId) {
        Room room = rooms.lock(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        return new MatchSession(getterRoom, saverRoom, teamScoreRepo, room);
    }

    /**
     * Открыть партию только для чтения — без блокировки.
     *
     * <p>Записывать такую сессию нельзя: {@link MatchSession#commit()} на ней
     * не вызывается. Отдельный вход нужен затем, что читают партию много и
     * часто — снимок, предикаты прав, панель арсенала, — и держать на каждом
     * таком чтении блокировку значило бы выстроить всю комнату в очередь.
     */
    public MatchSession readSession(String roomId) {
        Room room = rooms.read(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        return new MatchSession(getterRoom, saverRoom, teamScoreRepo, room);
    }

    /** Прочитать состояние партии, ничего не собираясь менять. */
    public MatchState read(String roomId) {
        return readSession(roomId).state();
    }
}
