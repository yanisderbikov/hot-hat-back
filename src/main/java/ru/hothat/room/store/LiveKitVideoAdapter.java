package ru.hothat.room.store;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.common.livekit.TurnCredentials;
import ru.hothat.room.port.VideoSessionPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Видеосвязь глазами комнаты: реализация {@link VideoSessionPort}.
 *
 * <p>Здесь нет ни одного решения о допуске — только подпись. Кого пускать,
 * решено раньше, в сценарии; сюда приезжает уже отобранный участник. Из-за
 * этого класс и лежит в {@code store}: он знает чужой протокол и больше
 * ничего, ровно как {@code LiveKitPresenceAdapter} в области партии.
 *
 * <p>Срок жизни токена — два часа, как и был. Он длиннее партии намеренно:
 * переподключение вкладки не должно упираться в протухший токен посреди хода.
 */
@Component
@RequiredArgsConstructor
public class LiveKitVideoAdapter implements VideoSessionPort {

    /** Столько живёт токен участника; переподключение вкладки в него укладывается. */
    private static final long TOKEN_TTL_SECONDS = 7200;

    private final LiveKitClient liveKit;
    private final TurnCredentials turn;

    @Value("${livekit.url:}")
    private String livekitUrl;

    @Override
    public String serverUrl() {
        return livekitUrl == null ? "" : livekitUrl;
    }

    @Override
    public String participantToken(Participant participant) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("roomId", participant.roomId());
        metadata.put("teamId", participant.teamId());
        metadata.put("role", participant.role());
        metadata.put("isTestBot", participant.testBot());
        metadata.put("gameNumber", participant.gameNumber());
        return liveKit.accessToken(new LiveKitClient.AccessTokenRequest(
                participant.identity(), participant.name(), participant.roomId(), metadata,
                participant.publisher(), true, true, false, false, false, false, true,
                TOKEN_TTL_SECONDS));
    }

    /**
     * Учётка ретранслятора: формула подписи общая с видео-чатом и живёт в
     * {@link TurnCredentials}; здесь только перевод в запись порта.
     */
    @Override
    public Optional<TurnTicket> turnTicket(String identity) {
        return turn.issue(identity).map(grant -> new TurnTicket(
                grant.urls(), grant.username(), grant.credential(), grant.ttlSeconds(), grant.expiresAtSeconds()));
    }
}
