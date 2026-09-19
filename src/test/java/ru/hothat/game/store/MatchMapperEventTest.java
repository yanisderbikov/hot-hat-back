package ru.hothat.game.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.game.domain.SabotageEvent;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Событие диверсии между JSON комнаты и доменной записью.
 *
 * <p>Реплику «мысли-облака» ({@code text}, {@code voiceId}) в JSON кладёт
 * прежний движок ботов, а читает экран из кадра канала; между ними — этот
 * маппер. Здесь проверяется, что реплика переживает путь туда и обратно, а
 * обычный выстрел от новых полей не получает ни одного лишнего ключа: его JSON
 * хранится в комнате и уходит пакетом LiveKit, и меняться ему незачем.
 */
class MatchMapperEventTest {

    private static Map<String, Object> shot(String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "sab-1");
        item.put("type", type);
        item.put("attackerUid", "bot-1");
        item.put("attackerName", "Бот");
        item.put("targetUid", "anya");
        item.put("createdAtMs", 1_000L);
        item.put("durationMs", 4_000L);
        item.put("gameNumber", 2);
        return item;
    }

    @Test
    @DisplayName("Реплика облака переживает путь JSON → событие → JSON")
    void thoughtCloudKeepsItsLine() {
        Map<String, Object> stored = shot("thought_cloud");
        stored.put("text", "Кажется, это про кота");
        stored.put("voiceId", "2");

        SabotageEvent event = MatchMapper.event(stored);
        assertThat(event.text()).isEqualTo("Кажется, это про кота");
        assertThat(event.voiceId()).isEqualTo("2");

        Map<String, Object> back = MatchMapper.eventAsJson(event);
        assertThat(back).containsEntry("text", "Кажется, это про кота").containsEntry("voiceId", "2");
    }

    @Test
    @DisplayName("Обычный выстрел не получает ключей text и voiceId — ни в JSON комнаты, ни в пакете")
    void ordinaryShotStaysUnchanged() {
        SabotageEvent event = MatchMapper.event(shot("tomato"));
        assertThat(event.text()).isNull();
        assertThat(event.voiceId()).isNull();

        assertThat(MatchMapper.eventAsJson(event)).doesNotContainKeys("text", "voiceId");

        @SuppressWarnings("unchecked")
        Map<String, Object> packet = new ObjectMapper().convertValue(event, LinkedHashMap.class);
        assertThat(packet).doesNotContainKeys("text", "voiceId");
        assertThat(packet).containsKey("memeTitle");  // прежние null-ключи пакета на месте
    }
}
