package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.RoomHostResponseDTO;
import ru.hothat.room.api.dto.TransferHostRequestDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomPresence;

/**
 * Передать комнату другому участнику.
 *
 * <p>Только до начала партии: во время игры хозяйство даёт право ставить
 * паузу и завершать матч, и отдавать его посреди хода означало бы менять
 * правила на ходу.
 *
 * <p>Цель обязана быть живой. Отдать комнату тому, чья вкладка закрылась,
 * значит оставить её без хозяина совсем: вернуть хозяйство сможет только
 * сторож бездействия, и то через три минуты полного состава.
 *
 * <p>Передача ручная и окончательная: {@code previousHostUid} чистится, потому
 * что возвращать комнату «прежнему» здесь нечего — он отдал её сам. Признак
 * возврата нужен только сторожу, который отнимает её без спроса.
 */
@Service
@RequiredArgsConstructor
public class TransferHostUseCase {

    /** Так помечена передача, сделанная руками, а не сторожем бездействия. */
    private static final String TRANSFER_MANUAL = "manual";

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomHostResponseDTO run(HotHatUser user, String roomId, TransferHostRequestDTO request) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        String targetUid = request.targetUid().trim();
        if (targetUid.equals(user.uid())) {
            throw ApiException.of("HOST_TRANSFER_TARGET_INVALID", 400);
        }
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        RoomPlayer target = getterRoom.getPlayer(roomId, targetUid)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));

        long now = System.currentTimeMillis();
        boolean alive = RoomPresence.playerAlive(Boolean.TRUE.equals(target.getIsTestBot()),
                target.getLastSeenAt() == null ? 0L : target.getLastSeenAt(), now);
        if (!alive) {
            throw ApiException.of("PLAYER_NOT_ACTIVE", 409);
        }

        room.setCreatedBy(targetUid);
        room.setPreviousHostUid(null);
        room.setHostTransferredAt(now);
        room.setHostTransferType(TRANSFER_MANUAL);
        room.setHostTransferredBy(user.uid());
        // Часы бездействия заводятся заново: новому хозяину дают полный срок,
        // а не остаток чужого.
        room.setHostLastSetupActivityAt(now);
        saverRoom.save(room);

        String name = target.getName() == null || target.getName().isBlank()
                ? "Игрок" : target.getName();
        return new RoomHostResponseDTO(roomId, targetUid, name, user.uid(), now);
    }
}
