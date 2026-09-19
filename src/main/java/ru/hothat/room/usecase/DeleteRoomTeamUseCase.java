package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomSeatingPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Распустить команду.
 *
 * <p>Переезд {@code deleteTeam()} ({@code app-core.js:12723}). Клиентская
 * транзакция читала состав команды, затем строку каждого её участника, чтобы
 * узнать, жив ли он, — до двух чтений на команду поверх чтения комнаты. Здесь
 * живость считается по одной выборке мест, а порог берётся из общего правила.
 *
 * <p>Распустить можно только пустую команду: иначе двое оказались бы в партии
 * без места, а очередь ходов — с командой без игроков. «Пустая» здесь значит
 * «без живых»: держать место за человеком, чья вкладка закрылась полчаса
 * назад, нечестно, а вычищать его отдельной кнопкой — лишний шаг.
 *
 * <p>Мёртвым участникам, если они всё же вернутся, команда обнуляется здесь же:
 * их строка не должна ссылаться на распущенную.
 */
@Service
@RequiredArgsConstructor
public class DeleteRoomTeamUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String roomId, String teamId) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        roomAuthz.requireMember(user, roomId);
        RoomTeam team = getterRoom.getTeam(roomId, teamId)
                .orElseThrow(() -> ApiException.of("ROOM_TEAM_NOT_FOUND", 404));

        long now = System.currentTimeMillis();
        List<RoomPlayer> alive = roomSeats.alivePlayers(roomId, now);
        int aliveMembers = (int) alive.stream()
                .filter(player -> teamId.equals(player.getTeamId()))
                .count();
        RoomSeatingPolicy.refuseTeamDeletion(RoomPhase.fromWire(room.getPhase()), aliveMembers)
                .ifPresent(refusal -> {
                    throw RoomRefusals.of(refusal);
                });

        for (RoomPlayer player : getterRoom.getPlayers(roomId)) {
            if (teamId.equals(player.getTeamId())) {
                player.setTeamId(null);
                saverRoom.savePlayer(player);
            }
        }
        saverRoom.deleteTeam(roomId, teamId);

        List<String> order = new ArrayList<>(
                room.getTeamOrder() == null ? List.<String>of() : room.getTeamOrder());
        order.remove(team.getTeamId());
        room.setTeamOrder(order);
        saverRoom.save(room);
    }
}
