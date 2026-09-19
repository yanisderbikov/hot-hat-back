package ru.hothat.conference.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.common.livekit.TurnCredentials;
import ru.hothat.conference.port.ConferenceVideoPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Видеосвязь глазами видео-чата: реализация {@link ConferenceVideoPort}.
 *
 * <p>Здесь нет ни одного решения о допуске — только подпись и два вызова
 * узла. Комната видеоузла зовётся {@code hot-hat-conf-<id>}: тот же
 * префикс, что у игровых комнат, плюс своё слово, чтобы созвон и партия
 * с совпадающим хвостом идентификатора никогда не оказались в одной комнате.
 *
 * <p>Токен живёт четыре часа: созвон длиннее партии, а переподключение
 * вкладки не должно упираться в протухший токен.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitConferenceAdapter implements ConferenceVideoPort {

    private static final long TOKEN_TTL_SECONDS = 4 * 3600;

    private final LiveKitClient liveKit;
    private final TurnCredentials turn;

    @Value("${livekit.url:}")
    private String livekitUrl;

    @Override
    public String serverUrl() {
        return livekitUrl == null ? "" : livekitUrl;
    }

    @Override
    public String participantToken(String conferenceId, String identity, String name) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("conferenceId", conferenceId);
        metadata.put("role", "conference");
        return liveKit.accessToken(new LiveKitClient.AccessTokenRequest(
                identity, name, roomId(conferenceId), metadata,
                true, true, true, false, false, false, false, true,
                TOKEN_TTL_SECONDS));
    }

    @Override
    public Optional<TurnTicket> turnTicket(String identity) {
        return turn.issue(identity).map(grant -> new TurnTicket(
                grant.urls(), grant.username(), grant.credential(), grant.ttlSeconds()));
    }

    @Override
    public List<String> liveIdentities(String conferenceId) {
        try {
            return liveKit.listParticipantIdentities(roomId(conferenceId));
        } catch (RuntimeException e) {
            log.warn("Состав звонка {} не прочитан: {}", conferenceId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void disconnect(String conferenceId, String identity) {
        try {
            liveKit.removeParticipant(roomId(conferenceId), identity);
        } catch (RuntimeException e) {
            log.warn("Участник {} не отключён от звонка {}: {}", identity, conferenceId, e.getMessage());
        }
    }

    /** {@code LiveKitClient.roomName} добавит общий префикс {@code hot-hat-}. */
    private static String roomId(String conferenceId) {
        return "conf-" + conferenceId;
    }
}
