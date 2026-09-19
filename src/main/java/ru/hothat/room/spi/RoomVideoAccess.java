package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.port.VideoSessionPort;
import ru.hothat.room.usecase.RoomVideoTokens;

/**
 * Пропуск в видеосвязь: реализация {@link RoomVideoPort}.
 *
 * <p>Решений здесь нет — они в {@link RoomVideoTokens}, где живёт отбор.
 * Класс существует ровно затем, чтобы у соседей была дверь, а у отбора —
 * один хозяин: без него лобби пришлось бы либо звать сценарий чужой области,
 * либо завести вторую копию правил допуска к видео.
 *
 * <p>Распространение {@code SUPPORTS}: пропуск просят и внутри транзакции
 * сценария (превью заводит строку зрителя тем же вызовом), и отдельным
 * чтением. Требовать свою транзакцию значило бы открывать вторую поверх
 * первой ради трёх чтений.
 */
@Service
@RequiredArgsConstructor
public class RoomVideoAccess implements RoomVideoPort {

    private final RoomVideoTokens tokens;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public VideoTicket playerTicket(HotHatUser user, String roomId, String participantIdentity) {
        return ticket(tokens.forPlayer(user, roomId, participantIdentity));
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public VideoTicket spectatorTicket(HotHatUser user, String roomId, String previewSession) {
        return ticket(tokens.forSpectator(user, roomId, previewSession));
    }

    private static VideoTicket ticket(RoomVideoTokens.Issued issued) {
        VideoSessionPort.TurnTicket turn = issued.turn();
        return new VideoTicket(issued.serverUrl(), issued.participantToken(),
                issued.participantIdentity(),
                turn == null ? null : new Turn(turn.urls(), turn.username(),
                        turn.credential(), turn.ttlSeconds(), turn.expiresAtSeconds()));
    }
}
