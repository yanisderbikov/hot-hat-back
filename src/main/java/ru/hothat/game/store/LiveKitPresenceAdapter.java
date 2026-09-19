package ru.hothat.game.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.game.port.MatchPresencePort;

import java.util.List;

/**
 * Присутствие по данным видеосвязи.
 *
 * <p>Недоступность LiveKit — не «никого нет»: по молчащему серверу видеосвязи
 * нельзя ставить партию на паузу и уж тем более засчитывать техническое
 * поражение. Поэтому отказ превращается в {@code unavailable}, а не в пустой
 * список.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitPresenceAdapter implements MatchPresencePort {

    private final LiveKitClient liveKit;

    @Override
    public Roster roster(String roomId) {
        try {
            List<String> identities = liveKit.listParticipantIdentities(roomId);
            return new Roster(true, List.copyOf(identities));
        } catch (RuntimeException e) {
            log.warn("Проверка присутствия в {} недоступна: {}", roomId, e.getMessage());
            return Roster.unavailable();
        }
    }

    @Override
    public void disconnect(String roomId, String identity) {
        try {
            liveKit.removeParticipant(roomId, identity);
        } catch (RuntimeException e) {
            log.debug("Не удалось отключить {} в {}: {}", identity, roomId, e.getMessage());
        }
    }
}
