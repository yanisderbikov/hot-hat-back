package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.api.dto.TestBotTurnAction;
import ru.hothat.testbot.api.dto.TestBotTurnResponseDTO;
import ru.hothat.testbot.port.TestBotEnginePort;

/**
 * Один шаг раннера: боты ходят, стреляют диверсиями и болтают в чате.
 *
 * <p>Шаг тикает примерно раз в секунду, поэтому здесь нет ничего лишнего:
 * проверка флага по кешу, вызов движка и перевод слова движка в закрытый
 * набор. Незнакомое слово не роняет шаг — раннер просто подождёт.
 */
@Service
@RequiredArgsConstructor
public class AdvanceTestBotTurnUseCase {

    private final TestBotEnginePort engine;
    private final TestRoomFeatureGuard featureGuard;

    @PreAuthorize("hasRole('ADMIN')")
    public TestBotTurnResponseDTO run(HotHatUser admin, String roomId) {
        featureGuard.requireEnabled();
        TestBotEnginePort.Turn turn = engine.advance(admin, roomId);
        return new TestBotTurnResponseDTO(
                turn.phase(),
                TestBotTurnAction.fromWire(turn.action()),
                turn.waitMs(),
                turn.botShots(),
                turn.botChat());
    }
}
