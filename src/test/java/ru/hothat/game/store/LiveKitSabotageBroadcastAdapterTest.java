package ru.hothat.game.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.game.domain.SabotageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Рассылка диверсии по каналу данных получает адресатов из события.
 *
 * <p>Сеть не нужна: клиент подменён наследником, который запоминает, что
 * ему передали. Проверяется именно граница «событие → пакет»: завеса кадра
 * бесполезна, если тот же пакет улетает всей комнате.
 */
class LiveKitSabotageBroadcastAdapterTest {

    /** Запоминает вызовы вместо похода в LiveKit. */
    private static class RecordingClient extends LiveKitClient {
        final List<List<String>> audiences = new ArrayList<>();
        final List<Map<String, Object>> payloads = new ArrayList<>();

        RecordingClient() {
            super("wss://livekit.example", "ключ", "секрет-длиной-побольше-тридцати-двух-байт",
                    WebClient.builder());
        }

        @Override
        public void sendSabotage(String roomId, Map<String, Object> event, List<String> audience) {
            payloads.add(event);
            audiences.add(audience);
        }
    }

    private static SabotageEvent event(String type) {
        return new SabotageEvent("sab-1", type, null, "clip-1", "turn-1", "attacker", "Аня", "target",
                1_000L, 10_000L, 1, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Выстрел уходит всей комнате: адресатов нет")
    void shotGoesToEveryone() {
        RecordingClient client = new RecordingClient();
        new LiveKitSabotageBroadcastAdapter(client, new ObjectMapper()).publish("room-1", event("tomato"));

        assertThat(client.audiences).containsExactly(List.of());
        assertThat(client.payloads.get(0)).containsEntry("type", "tomato");
    }

    @Test
    @DisplayName("Съёмка Подмены уходит только снимающему и снимаемому")
    void recordingGoesToTwo() {
        RecordingClient client = new RecordingClient();
        new LiveKitSabotageBroadcastAdapter(client, new ObjectMapper())
                .publish("room-1", event(SabotageEvent.RECORD_TYPE));

        assertThat(client.audiences).containsExactly(List.of("attacker", "target"));
        assertThat(client.payloads.get(0)).containsEntry("clipId", "clip-1");
    }

    @Test
    @DisplayName("Отказ канала данных не роняет диверсию: она уже записана в партию")
    void failureIsSwallowed() {
        LiveKitClient failing = new RecordingClient() {
            @Override
            public void sendSabotage(String roomId, Map<String, Object> event, List<String> audience) {
                throw new IllegalStateException("LiveKit недоступен");
            }
        };

        new LiveKitSabotageBroadcastAdapter(failing, new ObjectMapper()).publish("room-1", event("tomato"));
    }
}
