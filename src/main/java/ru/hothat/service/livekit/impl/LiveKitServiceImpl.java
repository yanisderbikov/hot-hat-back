package ru.hothat.service.livekit.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.service.livekit.LiveKitService;

import java.util.List;
import java.util.Map;

/**
 * Прежний фасад LiveKit: теперь только переходник к {@link LiveKitClient}.
 *
 * <p>Протокол переехал в общий клиент, к которому обращаются области. Здесь
 * не осталось ничего, кроме перекладывания запроса токена, — и это ровно то,
 * ради чего фасад ещё жив: старые адреса {@code /api/token}, {@code /api/game}
 * и {@code /api/admin} им пользуются, пока не сняты с эксплуатации.
 */
@Service
@RequiredArgsConstructor
public class LiveKitServiceImpl implements LiveKitService {

    private final LiveKitClient client;

    @Override
    public String accessToken(AccessTokenRequest request) {
        return client.accessToken(new LiveKitClient.AccessTokenRequest(
                request.identity(), request.name(), request.roomId(), request.metadata(),
                request.canPublish(), request.canSubscribe(), request.canPublishData(),
                request.hidden(), request.recorder(), request.roomRecord(), request.roomAdmin(),
                request.roomJoin(), request.ttlSeconds()));
    }

    @Override
    public String roomName(String roomId) {
        return client.roomName(roomId);
    }

    @Override
    public List<String> listParticipantIdentities(String roomId) {
        return client.listParticipantIdentities(roomId);
    }

    @Override
    public void removeParticipant(String roomId, String identity) {
        client.removeParticipant(roomId, identity);
    }

    @Override
    public void deleteRoom(String roomId) {
        client.deleteRoom(roomId);
    }

    /**
     * Прежний движок и боты стреляют только публичным оружием, поэтому здесь
     * рассылка всегда всей комнате. Адресную съёмку Подмены ведёт вторая
     * ветка — {@code LiveKitSabotageBroadcastAdapter} с адресатами события.
     */
    @Override
    public void sendSabotage(String roomId, Map<String, Object> event) {
        client.sendSabotage(roomId, event, List.of());
    }

    @Override
    public Map<String, Object> egress(String method, Map<String, Object> body) {
        return client.egress(method, body);
    }
}
