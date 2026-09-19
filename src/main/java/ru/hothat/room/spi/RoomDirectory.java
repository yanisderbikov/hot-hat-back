package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;
import ru.hothat.util.Json;

import java.util.Optional;

/**
 * Справка о комнате: реализация {@link RoomDirectoryPort}.
 *
 * <p>Здесь нет ни одного решения, кроме перевода строки комнаты в запись
 * порта, — и ровно два места, где перевод не механический, потому что сама
 * строка неоднозначна:
 *
 * <ul>
 *   <li><b>Хозяин.</b> В сегодняшней схеме хозяйство и авторство лежат в одной
 *       колонке {@code created_by}, и передача комнаты её переписывает. Порт
 *       называет это поле хозяином, а не автором: спрашивающим нужно именно
 *       «кто ведёт комнату сейчас». Когда комната переедет в {@code v2.room} и
 *       {@code v2.room_host}, поменяется этот метод, а не спрашивающие.
 *   <li><b>Владелец тестового прогона.</b> {@code test_owner_uid} появился
 *       позже {@code is_test_room}, и у старых комнат он пуст — отсюда падение
 *       на {@code created_by}. Условие записано здесь один раз; до этого оно
 *       жило двумя копиями, внутри движка ботов и в сторожевом классе.
 * </ul>
 *
 * <p>Распространение транзакции {@code SUPPORTS}: справку спрашивают и внутри
 * сценария с транзакцией, и до его начала — из сторожей прав. Требовать
 * транзакцию значило бы отвергать запрос ещё на проверке.
 */
@Service
@RequiredArgsConstructor
public class RoomDirectory implements RoomDirectoryPort {

    private final GetterRoom getterRoom;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean exists(String roomId) {
        return roomId != null && !roomId.isBlank() && getterRoom.getById(roomId).isPresent();
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<RoomBrief> find(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return Optional.empty();
        }
        return getterRoom.getById(roomId).map(RoomDirectory::brief);
    }

    private static RoomBrief brief(Room room) {
        boolean testRoom = Boolean.TRUE.equals(room.getIsTestRoom());
        String testOwner = room.getTestOwnerUid() != null && !room.getTestOwnerUid().isBlank()
                ? room.getTestOwnerUid() : Json.str(room.getCreatedBy());
        return new RoomBrief(
                room.getId(),
                room.getName(),
                room.getCreatedBy(),
                room.getPhase(),
                room.isClosed(),
                Boolean.TRUE.equals(room.getRanked()),
                testRoom,
                testRoom ? testOwner : null,
                room.getGameMode(),
                room.getDivisionLanguage(),
                room.getGameNumber() == null ? 0 : room.getGameNumber());
    }
}
