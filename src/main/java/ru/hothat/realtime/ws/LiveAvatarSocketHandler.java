package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.hothat.config.HotHatUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCRUM-46 — transient, authenticated, transparent LIVE avatars.
 *
 * A player publishes only a small WEBP of an already alpha-matted canvas.
 * The raw webcam, audio, masks and model inputs never reach this service.
 * Nothing is saved to a database or S3. Frames expire in 5 seconds.
 *
 * Viewers subscribe to explicit UIDs with a per-browser cap. All sender UIDs
 * are derived from the verified handshake, never a client frame field.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LiveAvatarSocketHandler extends TextWebSocketHandler {
    private static final int MAX_WATCHED = 32;
    private static final int MAX_FRAME_CHARS = 90_000;
    private static final int MIN_FRAME_INTERVAL_MS = 150;
    private static final long FRAME_TTL_MS = 5_000L;
    private static final String WEBP = "data:image/webp;base64,";
    private static final String PNG = "data:image/png;base64,";
    private final ObjectMapper mapper;

    private record Frame(String data, long time, String sessionId) {}
    private static final class Client {
        final WebSocketSession session;
        final String uid;
        volatile Set<String> watching = Set.of();
        volatile long lastPublished;
        Client(WebSocketSession session, String uid) {
            this.session = session;
            this.uid = uid;
        }
    }
    private final Map<String, Client> connected = new ConcurrentHashMap<>();
    private final Map<String, Frame> latest = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object principal = session.getAttributes().get(ChannelHandshakeAuthenticator.USER_ATTRIBUTE);
        if (!(principal instanceof HotHatUser user) || user.guest()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        connected.put(session.getId(), new Client(session, user.uid()));
        send(session, Map.of("type", "hello", "ttlMs", FRAME_TTL_MS));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Client sender = connected.get(session.getId());
        if (sender == null) return;
        String payload = message.getPayload();
        if (payload.length() > MAX_FRAME_CHARS + 512) return;
        JsonNode json;
        try { json = mapper.readTree(payload); }
        catch (Exception ignored) { return; }
        String type = json.path("type").asText("");
        switch (type) {
            case "ping" -> send(session, Map.of("type", "pong"));
            case "watch" -> {
                Set<String> ids = new HashSet<>();
                JsonNode uids = json.path("uids");
                if (uids.isArray()) {
                    for (JsonNode item : uids) {
                        String id = item.asText("");
                        // IDs are account identities, not arbitrary DOM/CSS.
                        if (id.matches("[A-Za-z0-9_-]{3,128}")) {
                            ids.add(id);
                            if (ids.size() >= MAX_WATCHED) break;
                        }
                    }
                }
                sender.watching = Set.copyOf(ids);
                long now = System.currentTimeMillis();
                // New friends/chat rows immediately receive the latest frame.
                for (String id : ids) {
                    Frame frame = latest.get(id);
                    if (frame != null && now - frame.time() < FRAME_TTL_MS)
                        send(session, Map.of("type", "frame", "uid", id,
                                "data", frame.data(), "time", frame.time()));
                }
            }
            case "frame" -> {
                String data = json.path("data").asText("");
                long now = System.currentTimeMillis();
                if (now - sender.lastPublished < MIN_FRAME_INTERVAL_MS) return;
                if (data.length() < 50 || data.length() > MAX_FRAME_CHARS
                        || !(data.startsWith(WEBP) || data.startsWith(PNG))) return;
                int base64Start = data.indexOf(',') + 1;
                if (!data.substring(base64Start).matches("[A-Za-z0-9+/=]+")) return;
                sender.lastPublished = now;
                Frame frame = new Frame(data, now, session.getId());
                latest.put(sender.uid, frame);
                publish(sender.uid, Map.of("type", "frame", "uid", sender.uid,
                        "data", data, "time", now));
                // Lazy eviction bounds memory even when senders disappear
                // without delivering a close event.
                if (latest.size() > 128)
                    latest.entrySet().removeIf(e -> now - e.getValue().time() > FRAME_TTL_MS);
            }
            case "off" -> {
                Frame frame = latest.get(sender.uid);
                if (frame != null && frame.sessionId().equals(session.getId())) {
                    latest.remove(sender.uid, frame);
                    publish(sender.uid, Map.of("type", "off", "uid", sender.uid));
                }
            }
            default -> { /* Unknown frames do not change subscriptions. */ }
        }
    }

    private void publish(String uid, Object frame) {
        for (Client viewer : connected.values()) {
            if (viewer.watching.contains(uid)) send(viewer.session, frame);
        }
    }

    private void send(WebSocketSession session, Object frame) {
        if (!session.isOpen()) return;
        synchronized (session) {
            if (!session.isOpen()) return;
            try { session.sendMessage(new TextMessage(mapper.writeValueAsString(frame))); }
            catch (IOException | IllegalStateException e) {
                log.debug("Live avatar viewer dropped: {}", e.toString());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Client client = connected.remove(session.getId());
        if (client == null) return;
        Frame frame = latest.get(client.uid);
        if (frame != null && frame.sessionId().equals(session.getId())) {
            latest.remove(client.uid, frame);
            // SCRUM-48: A background tab can lose its WebSocket while the
            // physical webcam remains open. Do not broadcast "off" on an
            // unexpected transport/peer disconnect: receivers must keep the
            // most recent matted portrait until the owner actually disables
            // the camera or closes the page. The sender's explicit "off"
            // command already removes the frame above, and a NORMAL socket
            // closure remains a true sign-out/exit.
            String signalType = status.getCode() == CloseStatus.NORMAL.getCode()
                    ? "off" : "paused";
            publish(client.uid, Map.of("type", signalType, "uid", client.uid));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        try { session.close(); } catch (IOException ignored) {}
    }
}
