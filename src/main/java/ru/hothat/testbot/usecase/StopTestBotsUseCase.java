package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.port.TestBotEnginePort;

/**
 * Распустить отряд и закрыть тестовую комнату.
 *
 * <p>Ответа нет: старый {@code {roomId, closed:true}} не нёс сведений, которых
 * не было бы в запросе. Адрес отвечает 204.
 */
@Service
@RequiredArgsConstructor
public class StopTestBotsUseCase {

    private final TestBotEnginePort engine;
    private final TestRoomFeatureGuard featureGuard;

    @PreAuthorize("hasRole('ADMIN')")
    public void run(HotHatUser admin, String roomId) {
        featureGuard.requireEnabled();
        engine.stop(admin, roomId);
    }
}
