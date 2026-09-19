package ru.hothat.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Тонкая обёртка над HTTP: ровно то, что раньше делалось curl'ом.
 *
 * <p>Клиент — {@link HttpClient} из JDK, а не {@code TestRestTemplate}. Тот
 * ходит через {@code HttpURLConnection}, а он на ответ 401 пытается
 * переспросить с удостоверением и падает своей ошибкой вместо кода ответа —
 * то есть именно на проверке «машинный адрес отказал» пользы от него нет.
 *
 * <p>Ответ разбирается в {@link JsonNode}, а не в DTO, намеренно. Тест смотрит
 * на поверхность так же, как браузер: пропавшее поле честно не найдётся, а
 * разбор в запись молча подставил бы {@code null}. Заодно виден сырой текст —
 * проверку «в ответах нет хеша пароля» иначе не написать.
 */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String origin;

    public ApiClient(int port) {
        this.origin = "http://localhost:" + port;
    }

    /** Ответ сервера: код, разобранное тело и его же сырой текст. */
    public record Response(int status, JsonNode body, String raw) {

        public JsonNode at(String path) {
            JsonNode node = body.at(path);
            if (node.isMissingNode()) {
                throw new AssertionError("В ответе нет узла " + path + ": " + raw);
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

    public Response get(String path, String token) {
        return send("GET", path, token, null, Map.of());
    }

    public Response post(String path, String token, Map<String, ?> body) {
        return send("POST", path, token, body, Map.of());
    }

    public Response put(String path, String token, Map<String, ?> body) {
        return send("PUT", path, token, body, Map.of());
    }

    /** Запрос без заголовка авторизации вовсе — не с пустым, а без него. */
    public Response anonymousPost(String path, Map<String, ?> body) {
        return send("POST", path, null, body, Map.of());
    }

    public Response send(String method, String path, String token, Map<String, ?> body,
                         Map<String, String> extraHeaders) {
        String payload = body == null ? "" : write(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, payload.isEmpty()
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        extraHeaders.forEach(request::header);
        try {
            HttpResponse<String> response = http.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String raw = response.body() == null ? "" : response.body();
            return new Response(response.statusCode(), parse(raw), raw);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(method + " " + path + " не дошёл: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос прерван", e);
        }
    }

    private static String write(Map<String, ?> body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось собрать тело запроса", e);
        }
    }

    private static JsonNode parse(String raw) {
        if (raw.isBlank()) {
            return JSON.createObjectNode();
        }
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            // 204 и страницы ошибок Tomcat телом-JSON не являются; тест смотрит
            // на код ответа, и падать на разборе тут не за что.
            return JSON.createObjectNode();
        }
    }
}
