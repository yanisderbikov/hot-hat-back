package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.HostHandoverResponseDTO;
import ru.hothat.room.domain.HostHandoverOutcome;
import ru.hothat.room.domain.HostHandoverPolicy;
import ru.hothat.room.domain.RoomPhase;

import java.util.List;

/**
 * Спросить сторожа: не пора ли отобрать комнату у бездействующего хозяина.
 *
 * <p>Заменяет {@code setup_host_watch} ({@code GameServiceImpl:392}) и чинит по
 * дороге две вещи, названные аудитом.
 *
 * <p>Первое — права. Тот метод был единственным в срезе без проверки участия,
 * хотя писал {@code room.createdBy} (находка A6): посторонний мог форсировать
 * передачу раньше срока и заодно прочитать состояние чужой публичной комнаты.
 * Здесь спрашивать вправе только участник — и это правильный уровень: сторожа
 * опрашивает как раз не-хозяин, потому что хозяин, проверяющий сам себя на
 * бездействие, бездействующим не бывает.
 *
 * <p>Второе — форма ответа. Пять несовместимых карт свелись к одной записи с
 * полем-перечислением (замечание C6): клиент перестаёт различать исходы по
 * наличию ключей {@code restored}, {@code transferred} и {@code remainingMs}.
 *
 * <p>Решение принимает чистая политика, а сценарий только читает состав,
 * применяет решение и переводит его в ответ. Три минуты бездействия иначе
 * нельзя было бы проверить, не прождав трёх минут.
 */
@Service
@RequiredArgsConstructor
public class HandOverIdleHostUseCase {

    /** Так подписан новый хозяин, у которого нет имени в комнате. */
    private static final String PLACEHOLDER_HOST = "хозяин комнаты";

    /** Так помечена передача, сделанная сторожем, а не руками. */
    private static final String TRANSFER_IDLE = "idle";

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public HostHandoverResponseDTO run(HotHatUser user, String roomId) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        roomAuthz.requireMember(user, roomId);

        long now = System.currentTimeMillis();
        List<RoomPlayer> alive = roomSeats.alivePlayers(roomId, now);
        HostHandoverPolicy.Facts facts = new HostHandoverPolicy.Facts(
                RoomPhase.fromWire(room.getPhase()),
                Boolean.TRUE.equals(room.getIsPrivate()),
                room.effectiveMaxPlayers(),
                room.getCreatedBy(),
                room.getPreviousHostUid(),
                room.getHostLastSetupActivityAt() == null ? 0L : room.getHostLastSetupActivityAt(),
                alive.stream().map(RoomPlayer::getUid).toList());
        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(facts, now);

        String newHostName = null;
        if (decision.outcome() == HostHandoverOutcome.RESTORED
                || decision.outcome() == HostHandoverOutcome.TRANSFERRED) {
            room.setCreatedBy(decision.newHostUid());
            room.setHostTransferredAt(now);
            room.setHostTransferredBy(null);
            room.setHostTransferType(TRANSFER_IDLE);
            // Передача по бездействию окончательна, а возврат прежнего —
            // однократен: и то и другое стирает признак возврата, иначе
            // комната ходила бы по кругу между двумя людьми.
            room.setPreviousHostUid(null);
            newHostName = nameOf(alive, decision.newHostUid());
        }
        if (decision.resetActivityClock()) {
            room.setHostLastSetupActivityAt(now);
        }
        if (decision.resetActivityClock() || newHostName != null) {
            saverRoom.save(room);
        }

        return new HostHandoverResponseDTO(
                decision.outcome(),
                decision.newHostUid(),
                newHostName,
                decision.remainingMs(),
                decision.alivePlayers(),
                decision.target());
    }

    private static String nameOf(List<RoomPlayer> players, String uid) {
        for (RoomPlayer player : players) {
            if (player.getUid().equals(uid)) {
                return player.getName() == null || player.getName().isBlank()
                        ? PLACEHOLDER_HOST : player.getName();
            }
        }
        return PLACEHOLDER_HOST;
    }
}
