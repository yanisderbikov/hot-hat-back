package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.ClosedRoomResponseDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomPresence;

import java.time.Instant;
import java.util.List;

/**
 * Закрыть комнату решением хозяина.
 *
 * <p>Заменяет {@code POST /api/cleanup-rooms?room_id=}, который до сих пор звал
 * за хозяина браузер ({@code app-core.js:1048}). Тот адрес не принимал
 * личность вовсе — проверять было нечего (находка A2), — и любой желающий мог
 * снести чужую комнату по идентификатору, который ходит по чатам
 * ссылкой-приглашением.
 *
 * <p>Два исхода, и различает их не хозяин, а состав. Если, кроме него, живых
 * людей в комнате нет, стирается всё дерево: держать пустую комнату незачем, и
 * именно так поступает уборщик. Если кто-то ещё сидит, комната помечается
 * закрытой и остаётся строкой: этим людям нужно показать «хозяин закрыл
 * комнату», а не пустой экран с ненайденной комнатой.
 *
 * <p>Тест-боты в счёт не идут: комната, где остались одни боты, — брошенная.
 */
@Service
@RequiredArgsConstructor
public class CloseRoomUseCase {

    /** Так подписан закрытый хозяином: причина уезжает в журнал и в витрину. */
    private static final String CLOSED_BY_HOST = "host_closed";

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ClosedRoomResponseDTO run(HotHatUser user, String roomId) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        long now = System.currentTimeMillis();

        List<RoomPlayer> players = getterRoom.getPlayers(roomId);
        boolean someoneStays = players.stream()
                .filter(player -> !Boolean.TRUE.equals(player.getIsTestBot()))
                .filter(player -> !player.getUid().equals(user.uid()))
                .anyMatch(player -> RoomPresence.playerAlive(false,
                        player.getLastSeenAt() == null ? 0L : player.getLastSeenAt(), now));

        if (!someoneStays) {
            saverRoom.deleteRoomTree(roomId);
            return new ClosedRoomResponseDTO(roomId, true, now);
        }
        room.setPhase(RoomPhase.CLOSED.wireValue());
        room.setClosedAt(Instant.ofEpochMilli(now));
        room.setClosedBy(user.uid());
        room.setClosedReason(CLOSED_BY_HOST);
        saverRoom.save(room);
        return new ClosedRoomResponseDTO(roomId, false, now);
    }
}
