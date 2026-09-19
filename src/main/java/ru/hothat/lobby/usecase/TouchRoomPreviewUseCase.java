package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.RoomPreviewHeartbeatResponseDTO;

/**
 * Подтвердить, что превью ещё смотрят.
 *
 * <p>Заменяет {@code updateDoc spectators.lastSeenAt} из браузера
 * ({@code live-preview.js:102}): отметку ставит сервер своими часами. У
 * клиента часы могут уехать на минуты, и зритель с забежавшими вперёд часами
 * оставался «живым» в комнате навсегда.
 *
 * <p>Закрытая или доигранная комната отвечает {@code ROOM_NOT_FOUND}, и это
 * же говорит клиенту, что смотреть больше нечего. Раньше он узнавал об этом
 * из исчезнувшего документа комнаты — то есть из подписки, которой здесь
 * больше нет.
 */
@Service
@RequiredArgsConstructor
public class TouchRoomPreviewUseCase {

    private final RoomPreviewAccess access;
    private final RoomPreviewSeats seats;

    @PreAuthorize("hasAnyRole('GUEST','USER')")
    @Transactional
    public RoomPreviewHeartbeatResponseDTO run(HotHatUser user, String roomId) {
        access.requireWatchable(roomId, user.uid());
        long lastSeenAtMs = seats.touch(roomId, user.uid());
        return new RoomPreviewHeartbeatResponseDTO(roomId, lastSeenAtMs, LobbyReadLimits.PREVIEW_HEARTBEAT_MS);
    }
}
