package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.admin.api.dto.AdminRoomClosureResponseDTO;
import ru.hothat.admin.api.dto.CloseRoomByAdminRequestDTO;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.util.Json;

import java.time.Instant;

/**
 * Закрыть комнату решением администратора.
 *
 * <p>Заменяет {@code POST /api/admin} с {@code action=close_room}.
 *
 * <p>Повторное закрытие уже закрытой комнаты отвечает тем же самым: это
 * идемпотентная операция, а не конфликт. Администратор жмёт кнопку, увидев
 * список комнат, который мог устареть за секунды, и получать 409 за то, что
 * комнату закрыл кто-то другой, ему незачем — цель достигнута.
 *
 * <p>Снос видеокомнаты уходит за коммит: это внешний вызов.
 */
@Service
@RequiredArgsConstructor
public class CloseRoomByAdminUseCase {

    private static final String DEFAULT_REASON = "Закрыто администратором";
    private static final String CLOSED_PHASE = "closed";

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final ApplicationEventPublisher events;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminRoomClosureResponseDTO run(HotHatUser admin, String roomId,
                                           CloseRoomByAdminRequestDTO request) {
        Room room = getterRoom.getById(roomId).orElseThrow(ErrorCode.ROOM_NOT_FOUND::raise);
        int playersAtClosure = getterRoom.getPlayers(roomId).size();

        String reason = Json.str(request == null || request.reason() == null
                ? DEFAULT_REASON : request.reason(), 200);
        Instant closedAt = Instant.now();
        room.setPhase(CLOSED_PHASE);
        room.setClosedAt(closedAt);
        room.setClosedBy(admin.uid());
        room.setClosedReason(reason);
        saverRoom.save(room);

        events.publishEvent(new AdminEvictionEvents.RoomClosedByAdmin(roomId));
        return new AdminRoomClosureResponseDTO(roomId, CLOSED_PHASE, admin.uid(),
                closedAt.toEpochMilli(), reason, playersAtClosure);
    }
}
