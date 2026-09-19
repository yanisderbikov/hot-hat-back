package ru.hothat.testbot.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.testbot.port.SpeechSynthesisPort;
import ru.hothat.util.Json;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Синтез речи через Edge Read Aloud: реализация {@link SpeechSynthesisPort}.
 *
 * <p>Тот же сервис, что использовал node-edge-tts. Протокол неофициальный:
 * сменит его Microsoft — адрес ответит 502, а игра продолжится без озвучки.
 *
 * <p>Клиент лежит в {@code store} области ботов, а не в общем месте: озвучка
 * нужна ровно одному — «облаку мыслей» бота, и общего места на одного
 * потребителя не бывает.
 */
@Slf4j
@Component
public class EdgeSpeechClient implements SpeechSynthesisPort {

    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String ENDPOINT =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken="
                    + TRUSTED_CLIENT_TOKEN;
    private static final String OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3";
    private static final int MAX_TEXT_CHARS = 80;
    private static final int MAX_AUDIO_BYTES = 2_000_000;
    private static final int CACHE_MAX = 48;
    /** Смещение эпохи Windows (1601 → 1970) в секундах: нужно для токена Sec-MS-GEC. */
    private static final long WIN_EPOCH_SECONDS = 11644473600L;

    private record Voice(String id, String label, String voice, String lang, String rate, String pitch) {
    }

    private static final Map<String, Voice> VOICES = Map.of(
            "1", new Voice("1", "Мужской", "ru-RU-DmitryNeural", "ru-RU", "+0%", "+0Hz"),
            "2", new Voice("2", "Женский", "ru-RU-SvetlanaNeural", "ru-RU", "+0%", "+0Hz"),
            "3", new Voice("3", "Смешной", "en-US-AnaNeural", "ru-RU", "+7%", "+8Hz"));

    /** Небольшой LRU: одни и те же реплики ботов повторяются часто. */
    private final Map<String, Speech> cache =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Speech> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    @Override
    public Speech synthesize(String rawText, String voiceId) {
        String text = clean(rawText);
        if (text.isEmpty()) {
            throw ApiException.of("TTS_TEXT_REQUIRED");
        }
        Voice voice = VOICES.getOrDefault(Json.str(voiceId == null ? "1" : voiceId), VOICES.get("1"));
        String cacheKey = voice.id() + "\n" + text;
        Speech cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        byte[] audio = requestAudio(text, voice);
        if (audio.length == 0 || audio.length > MAX_AUDIO_BYTES) {
            throw ApiException.of("TTS_AUDIO_INVALID", 502);
        }
        Speech speech = new Speech("audio/mpeg", Base64.getEncoder().encodeToString(audio),
                voice.id(), voice.label(), voice.voice());
        cache.put(cacheKey, speech);
        return speech;
    }

    private static String clean(String value) {
        String text = Json.str(value)
                .replaceAll("[\\u0000-\\u001f\\u007f]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    private byte[] requestAudio(String text, Voice voice) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        AudioCollector collector = new AudioCollector();
        WebSocket socket = null;
        try {
            socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(ENDPOINT + "&Sec-MS-GEC=" + secMsGec()
                            + "&Sec-MS-GEC-Version=1-130.0.2849.68"), collector)
                    .get(12, TimeUnit.SECONDS);

            socket.sendText(configMessage(), true).get(5, TimeUnit.SECONDS);
            socket.sendText(ssmlMessage(requestId, text, voice), true).get(5, TimeUnit.SECONDS);
            collector.done.get(20, TimeUnit.SECONDS);
            return collector.audio.toByteArray();
        } catch (Exception e) {
            log.warn("Синтез речи не удался: {}", e.getMessage());
            throw ApiException.of("TTS_UNAVAILABLE", 502);
        } finally {
            if (socket != null) {
                socket.abort();
            }
        }
    }

    /** Подпись запроса: SHA-256 от «тиков» с округлением до 5 минут и общего токена. */
    private static String secMsGec() {
        long seconds = System.currentTimeMillis() / 1000 + WIN_EPOCH_SECONDS;
        seconds -= seconds % 300;
        java.math.BigInteger ticks = java.math.BigInteger.valueOf(seconds)
                .multiply(java.math.BigInteger.valueOf(10_000_000L));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((ticks + TRUSTED_CLIENT_TOKEN).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось подписать запрос синтеза", e);
        }
    }

    private static String timestamp() {
        return new java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                Locale.US).format(new Date());
    }

    private static String configMessage() {
        return "X-Timestamp:" + timestamp() + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"" + OUTPUT_FORMAT + "\"}}}}";
    }

    private static String ssmlMessage(String requestId, String text, Voice voice) {
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='"
                + voice.lang() + "'><voice name='" + voice.voice() + "'>"
                + "<prosody pitch='" + voice.pitch() + "' rate='" + voice.rate() + "' volume='+0%'>"
                + escapeXml(text) + "</prosody></voice></speak>";
        return "X-RequestId:" + requestId + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + timestamp() + "Z\r\n"
                + "Path:ssml\r\n\r\n" + ssml;
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * Аудио приходит бинарными кадрами: первые два байта — длина заголовка,
     * дальше сам заголовок и MP3-данные. Текстовый кадр Path:turn.end закрывает поток.
     */
    private static final class AudioCollector implements WebSocket.Listener {

        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private final ByteArrayOutputStream binaryChunk = new ByteArrayOutputStream();
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private final StringBuilder textChunk = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textChunk.append(data);
            if (last) {
                if (textChunk.indexOf("Path:turn.end") >= 0) {
                    done.complete(null);
                }
                textChunk.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            binaryChunk.writeBytes(chunk);
            if (last) {
                byte[] frame = binaryChunk.toByteArray();
                binaryChunk.reset();
                if (frame.length >= 2) {
                    int headerLength = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
                    int start = 2 + headerLength;
                    if (start < frame.length) {
                        audio.write(frame, start, frame.length - start);
                    }
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            done.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            done.completeExceptionally(error);
        }
    }
}
