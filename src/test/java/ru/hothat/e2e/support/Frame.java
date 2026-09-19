package ru.hothat.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Кадр канала: разобранное тело и его же сырой текст.
 *
 * <p>Тот же взгляд, что у {@link ApiClient.Response}: смотрим на поверхность
 * так же, как браузер, и пропавший узел честно не находится. Сырой текст
 * нужен проверкам «этого в кадре быть не должно» — идентификатор чужого мема
 * ищется по всему кадру, а не в поле, где его ждут.
 */
public record Frame(JsonNode body, String raw) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static Frame parse(String raw) {
        try {
            return new Frame(JSON.readTree(raw), raw);
        } catch (Exception e) {
            throw new AssertionError("Кадр канала — не JSON: " + raw, e);
        }
    }

    /** Тип кадра: {@code hello}, {@code room}, {@code error} и прочие. */
    public String type() {
        return body.path("type").asText("");
    }

    public JsonNode at(String path) {
        JsonNode node = body.at(path);
        if (node.isMissingNode()) {
            throw new AssertionError("В кадре нет узла " + path + ": " + raw);
        }
        return node;
    }

    public String text(String path) {
        return at(path).asText();
    }

    public int number(String path) {
        return at(path).asInt();
    }
}
