package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.RoomPreviewSessionResponseDTO;
import ru.hothat.room.spi.RoomVideoPort;
import ru.hothat.util.Ids;

/**
 * Пустить наблюдателя в видеопревью комнаты.
 *
 * <p>Одна операция вместо двух клиентских вызовов: браузер сам заводил себе
 * строку зрителя ({@code live-preview.js:101}) и отдельно просил видеотокен
 * ({@code :104}). Между ними жило состояние «зритель есть, токена нет», а
 * между ними же клиент мог передумать — и убирать за ним было некому.
 *
 * <p>Идентификатор сессии выдаёт сервер. Раньше его придумывал браузер, и он
 * же становился частью имени участника видеосвязи: назвавшись чужой сессией,
 * можно было выбить из комнаты другого зрителя.
 *
 * <p>Токен подписывается локально, без похода в LiveKit
 * ({@code LiveKitServiceImpl.accessToken} — это сборка JWT), поэтому запрет на
 * внешние вызовы внутри транзакции здесь не нарушается.
 *
 * <p>Сам пропуск выдаёт область комнаты через свой порт: кого пускать смотреть,
 * решает она, и вторая копия этого правила в лобби разошлась бы с первой на
 * первой же правке приватности.
 *
 * <p>Транзакция открывается явно, а не аннотацией, ради одного повтора. Две
 * вкладки одного человека получают кадр лобби одновременно и обе просят превью
 * одной комнаты: обе не находят места, обе вставляют, и проигравшая ловит
 * {@code room_spectator_pkey}. Отказывать ей нечем — место есть, просто его
 * завёл сосед; второй заход в свежей транзакции находит строку и продлевает
 * её. Внутри той же транзакции повторить нельзя: после нарушения ограничения
 * она только откатывается.
 */
@Service
@RequiredArgsConstructor
public class OpenRoomPreviewUseCase {

    private final RoomPreviewAccess access;
    private final RoomPreviewSeats seats;
    private final RoomVideoPort roomVideo;
    private final TransactionTemplate transactions;

    @PreAuthorize("hasAnyRole('GUEST','USER')")
    public RoomPreviewSessionResponseDTO run(HotHatUser user, String roomId) {
        try {
            return transactions.execute(status -> open(user, roomId));
        } catch (DataIntegrityViolationException race) {
            return transactions.execute(status -> open(user, roomId));
        }
    }

    private RoomPreviewSessionResponseDTO open(HotHatUser user, String roomId) {
        access.requireWatchable(roomId, user.uid());
        seats.open(roomId, user.uid(), user.name());

        String sessionId = Ids.hex(8);
        // Пропуск зрителя даёт только приём: показывать себя из зала нельзя, а
        // в приватную комнату он не пускает вовсе.
        RoomVideoPort.VideoTicket ticket = roomVideo.spectatorTicket(user, roomId, sessionId);

        return new RoomPreviewSessionResponseDTO(
                roomId,
                sessionId,
                ticket.serverUrl(),
                ticket.participantToken(),
                LobbyReadLimits.PREVIEW_HEARTBEAT_MS);
    }
}
