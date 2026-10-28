package ru.hothat.codeteam.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.hothat.codeteam.api.dto.CodeTeamPilotRequestDTO;
import ru.hothat.codeteam.api.dto.CodeTeamPilotResponseDTO;
import ru.hothat.config.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Один ручной запрос без ретраев; появление ключа само ничего не списывает. */
@Service
public class RunCodeTeamPilotUseCase {

    private static final long PAID_CALL_COOLDOWN_MS = 60_000;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private long lastPaidAttemptMs;

    @Value("${code-team.pilot.enabled}")
    private boolean enabled;

    @Value("${CODE_TEAM_MODEL:gemini-3-8-flash}")
    private String model;

    @Value("${GENAPI_API_KEY:}")
    private String apiKey;

    public RunCodeTeamPilotUseCase(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized CodeTeamPilotResponseDTO run(CodeTeamPilotRequestDTO input) {
        CodeTeamPilotPrompt.Prepared prepared = CodeTeamPilotPrompt.prepare(input);
        if (!input.live()) {
            return new CodeTeamPilotResponseDTO("dry-run", model,
                    prepared.evidenceCount(), prepared.prompt(), null);
        }
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            throw ApiException.of("CODE_TEAM_PILOT_DISABLED", 403);
        }
        long now = System.currentTimeMillis();
        if (lastPaidAttemptMs != 0 && now - lastPaidAttemptMs < PAID_CALL_COOLDOWN_MS) {
            throw ApiException.of("CODE_TEAM_PILOT_COOLDOWN", 429);
        }
        // Фиксируем попытку до HTTP: даже при таймауте провайдер мог списать кредит.
        lastPaidAttemptMs = now;
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 750,
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Ты аналитик игровых взаимодействий. Дай короткий отчёт на русском, только по доказательствам."),
                            Map.of("role", "user", "content", prepared.prompt()))));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://proxy.gen-api.ru/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> result = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                throw ApiException.of("CODE_TEAM_PROVIDER_UNAVAILABLE", 502);
            }
            JsonNode response = mapper.readTree(result.body());
            String analysis = response.path("choices").path(0).path("message").path("content").asText("");
            if (analysis.isBlank()) {
                throw ApiException.of("CODE_TEAM_PROVIDER_UNAVAILABLE", 502);
            }
            return new CodeTeamPilotResponseDTO("live", model, prepared.evidenceCount(), null, analysis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.of("CODE_TEAM_PROVIDER_UNAVAILABLE", 502);
        } catch (IOException e) {
            throw ApiException.of("CODE_TEAM_PROVIDER_UNAVAILABLE", 502);
        }
    }
}
