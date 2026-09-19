package ru.hothat.testbot.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.HotHatUser;
import ru.hothat.service.testbots.TestBotsService;
import ru.hothat.testbot.port.TestBotEnginePort;
import ru.hothat.util.Json;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Переходник к ещё не переехавшему движку ботов.
 *
 * <p>Единственное место области, знающее, что движок отвечает картами. Раньше
 * разбор карт стоял переводчиком рядом со сценариями; здесь он там, где ему
 * место, — за портом, в {@code store}.
 *
 * <p>Заодно чинится счёт выстрелов. Движок кладёт под ключ {@code botShots}
 * СПИСОК видов диверсий, а прежний переводчик читал его числом — и получал
 * ноль всегда, то есть боевой шаг выглядел мирным. Здесь считается длина
 * списка; форма ответа не меняется, поле как было целым, так и осталось.
 */
@Component
@RequiredArgsConstructor
public class LegacyBotEngineAdapter implements TestBotEnginePort {

    /** Движок при исчезнувшей комнате отвечает {roomId, closed} вместо шага. */
    private static final long CLOSED_ROOM_WAIT_MS = 30000;
    private static final long DEFAULT_WAIT_MS = 5000;

    private final TestBotsService engine;

    @Override
    public Squad setUp(HotHatUser owner, String roomId) {
        Map<String, Object> raw = engine.setup(owner, roomId);
        return new Squad(
                Json.str(raw.get("roomId")),
                strings(raw.get("botIds")),
                strings(raw.get("teamOrder")),
                (int) Json.num(raw.get("maxPlayers"), 0));
    }

    @Override
    public void stop(HotHatUser owner, String roomId) {
        // Ответ движка {roomId, closed:true} не нёс сведений, которых не было бы
        // в запросе, и наружу не выходит: адрес отвечает 204.
        engine.stop(owner, roomId);
    }

    @Override
    public Turn advance(HotHatUser owner, String roomId) {
        Map<String, Object> raw = engine.tick(owner, roomId);
        if (Boolean.TRUE.equals(raw.get("closed"))) {
            // У этой ветки движка нет ни фазы, ни задержки: называем их здесь,
            // чтобы у сценария был один ответ на все исходы.
            return new Turn("closed", "idle", CLOSED_ROOM_WAIT_MS, null, null);
        }
        return new Turn(
                Json.str(raw.get("phase")),
                Json.str(raw.get("action")),
                Json.num(raw.get("waitMs"), DEFAULT_WAIT_MS),
                shots(raw.get("botShots")),
                raw.get("botChat") == null ? null : Boolean.TRUE.equals(raw.get("botChat")));
    }

    @Override
    public Shot fireOwnerFart(HotHatUser owner, String roomId, String eventId) {
        // Движок читает тело картой. Формат идентификатора проверен на входе
        // аннотацией DTO, здесь остаётся только переложить.
        Map<String, Object> body = new LinkedHashMap<>();
        if (eventId != null) {
            body.put("event_id", eventId);
        }
        Map<String, Object> event = Json.map(engine.fireOwnerFart(owner, roomId, body).get("event"));
        return new Shot(
                Json.str(event.get("id")),
                Json.str(event.get("type")),
                Json.str(event.get("attackerUid")),
                Json.str(event.get("attackerName")),
                blankToNull(Json.str(event.get("targetUid"))),
                Json.num(event.get("createdAtMs"), 0),
                Json.num(event.get("durationMs"), 0),
                (int) Json.num(event.get("gameNumber"), 0));
    }

    /** Ключа нет — шаг был не боевой; это не то же самое, что ноль выстрелов. */
    private static Integer shots(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Collection<?> list ? list.size() : (int) Json.num(value, 0);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return (List<String>) (List<?>) list.stream().map(String::valueOf).toList();
    }
}
