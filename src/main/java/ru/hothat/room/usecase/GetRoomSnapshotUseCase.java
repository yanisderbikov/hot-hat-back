package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;
import ru.hothat.room.api.dto.RoomSnapshotResponseDTO;

/**
 * Отдать комнату целиком: паспорт, места, составы и зрителей.
 *
 * <p>Заменяет четыре живые подписки браузера сразу — комната, игроки, команды,
 * зрители ({@code app-core.js:8750-8806}). Четырьмя они были не по смыслу, а
 * по устройству прежнего хранилища; приезжали вразнобой, и стол успевал
 * мигнуть пустым.
 *
 * <p>Три чтения на весь ответ, все три — выборки по комнате. Ни одного чтения
 * в цикле по составу: имена и аватары лежат в самих местах, снимками на момент
 * входа, и ходить за карточкой каждого игрока не нужно.
 *
 * <p>Транзакция на чтение объявлена {@code readOnly}: снимок берётся один на
 * все четыре выборки, иначе состав мог бы приехать из состояния до чужой
 * пересадки, а команды — после.
 */
@Service
@RequiredArgsConstructor
public class GetRoomSnapshotUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public RoomSnapshotResponseDTO run(HotHatUser user, String roomId) {
        // Сначала комната, потом место: у удалённой комнаты мест нет ни у кого,
        // и с обратным порядком её бывший игрок получал бы ROOM_MEMBER_ONLY —
        // тот же отказ, что и выгнанный. Экран различает два кода
        // (handleRoomChannelRefusal): «комната удалена» и «доступ закрыт».
        Room room = roomAuthz.requireRoom(roomId);
        RoomAccessGuard.Seat viewerSeat = roomAuthz.requireSeat(user, roomId);
        long now = System.currentTimeMillis();
        return new RoomSnapshotResponseDTO(
                projections.room(room),
                projections.seats(getterRoom.getPlayers(roomId), now),
                projections.teams(getterRoom.getTeams(roomId)),
                projections.spectators(getterRoom.getSpectators(roomId)),
                viewerSeat.kind());
    }
}
