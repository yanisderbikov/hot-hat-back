package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.RoomSeatHeartbeatResponseDTO;
import ru.hothat.room.domain.RoomPresence;

/**
 * Отметить, что игрок ещё в комнате.
 *
 * <p>Чистое обновление отметки и ничего больше — ни передачи хозяйства, ни
 * уборки, ни сверки состава с видеосвязью. Это осознанное разделение: раньше
 * тот же тик раз в полминуты был поводом сделать всё сразу, и обычная отметка
 * присутствия могла отобрать у человека комнату или поставить партию на паузу.
 * Сторож хозяйства теперь спрашивают отдельным адресом, уборку — своим.
 *
 * <p>В ответе серверное время. Разница с клиентским и есть поправка часов,
 * ради которой фронтенд до сих пор писал себе в строку {@code clockProbeAt} и
 * тут же перечитывал её с сервера ({@code app-core.js:8053}) — то есть платил
 * записью в базу за чтение часов.
 */
@Service
@RequiredArgsConstructor
public class TouchRoomSeatUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomSeatHeartbeatResponseDTO run(HotHatUser user, String roomId) {
        RoomPlayer player = roomAuthz.requireMember(user, roomId);
        long now = System.currentTimeMillis();
        player.setLastSeenAt(now);
        saverRoom.savePlayer(player);
        return new RoomSeatHeartbeatResponseDTO(now, now, RoomPresence.PLAYER_WINDOW_MS);
    }
}
