package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.api.dto.TestBotSquadResponseDTO;
import ru.hothat.testbot.port.TestBotEnginePort;

/**
 * Поднять отряд ботов в тестовой комнате.
 *
 * <p>Транзакция не объявлена здесь намеренно: подъём отряда — одна операция
 * движка, и она транзакционна внутри. Обернуть её ещё раз значило бы растянуть
 * транзакцию на чтение каталога мемов, которое ходит в чужую область.
 */
@Service
@RequiredArgsConstructor
public class SetUpTestBotsUseCase {

    private final TestBotEnginePort engine;
    private final TestRoomFeatureGuard featureGuard;

    @PreAuthorize("hasRole('ADMIN')")
    public TestBotSquadResponseDTO run(HotHatUser admin, String roomId) {
        featureGuard.requireEnabled();
        TestBotEnginePort.Squad squad = engine.setUp(admin, roomId);
        return new TestBotSquadResponseDTO(
                squad.roomId(), squad.botIds(), squad.teamOrder(), squad.maxPlayers());
    }
}
