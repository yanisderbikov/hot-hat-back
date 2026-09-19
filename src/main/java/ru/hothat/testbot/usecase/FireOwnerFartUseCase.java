package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.api.dto.FireOwnerFartRequestDTO;
import ru.hothat.testbot.api.dto.FiredOwnerFartResponseDTO;
import ru.hothat.testbot.api.dto.SabotageEventView;
import ru.hothat.testbot.port.TestBotEnginePort;

/**
 * Ручная диверсия владельца: событие без боезапаса и кулдауна.
 *
 * <p>Идентификатор события приходит от клиента, чтобы повторная отправка не
 * проиграла один и тот же звук дважды; его формат проверяет DTO на входе.
 */
@Service
@RequiredArgsConstructor
public class FireOwnerFartUseCase {

    private final TestBotEnginePort engine;
    private final TestRoomFeatureGuard featureGuard;

    @PreAuthorize("hasRole('ADMIN')")
    public FiredOwnerFartResponseDTO run(HotHatUser admin, String roomId, FireOwnerFartRequestDTO request) {
        featureGuard.requireEnabled();
        TestBotEnginePort.Shot shot = engine.fireOwnerFart(
                admin, roomId, request == null ? null : request.eventId());
        return new FiredOwnerFartResponseDTO(new SabotageEventView(
                shot.id(), shot.type(), shot.attackerUid(), shot.attackerName(),
                shot.targetUid(), shot.createdAtMs(), shot.durationMs(), shot.gameNumber()));
    }
}
