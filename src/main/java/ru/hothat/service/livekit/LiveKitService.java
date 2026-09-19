package ru.hothat.service.livekit;

import java.util.List;
import java.util.Map;

/**
 * Замена livekit-server-sdk. SDK для Node в Java нет, поэтому здесь прямая
 * работа с тем же протоколом: JWT доступа + twirp-вызовы RoomService/Egress.
 */
public interface LiveKitService {

    /** Токен участника комнаты (игрок, зритель, скрытый рекордер). */
    String accessToken(AccessTokenRequest request);

    /** Имя комнаты в LiveKit: hot-hat-<roomId>. */
    String roomName(String roomId);

    List<String> listParticipantIdentities(String roomId);

    void removeParticipant(String roomId, String identity);

    void deleteRoom(String roomId);

    /** Надёжная рассылка диверсии в топик hot-hat-sabotage. */
    void sendSabotage(String roomId, Map<String, Object> event);

    /** twirp-вызов livekit.Egress; используется записью игр. */
    Map<String, Object> egress(String method, Map<String, Object> body);

    record AccessTokenRequest(String identity,
                              String name,
                              String roomId,
                              Map<String, Object> metadata,
                              boolean canPublish,
                              boolean canSubscribe,
                              boolean canPublishData,
                              boolean hidden,
                              boolean recorder,
                              boolean roomRecord,
                              boolean roomAdmin,
                              boolean roomJoin,
                              long ttlSeconds) {
    }
}
