package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.hothat.config.HotHatProperties;
import ru.hothat.util.Json;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Отправитель писем о расходе — с журналом.
 *
 * <p>Письмо заводится в журнале ДО попытки отправки и только потом уходит.
 * Порядок именно такой: неотправленное письмо — тоже история, и вопрос «почему
 * не пришёл вчерашний отчёт» должен иметь ответ в базе, а не в логе процесса.
 *
 * <p>Раньше тело письма и результат отправки лежали двумя jsonb-полями внутри
 * самой тревоги и самого отчёта, а рядом жила таблица-счётчик отправленных.
 * Теперь письмо — строка журнала, а счётчик выводится из него запросом.
 *
 * <p>Ненастроенный ключ отправителя не исключение: письма просто не уходят, и
 * это видно в сводке «здоровье систем» отдельной строкой предупреждения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageMailer {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String ENDPOINT = "https://api.resend.com/emails";
    private static final String DEFAULT_FROM = "HOT-HAT Monitor <onboarding@resend.dev>";
    private static final int REASON_LIMIT = 200;

    private final UsageStore store;
    private final HotHatProperties properties;
    private final WebClient.Builder webClientBuilder;

    @Value("${report.resend-api-key:}")
    private String resendApiKey;

    @Value("${report.email:}")
    private String reportEmail;

    @Value("${report.from:}")
    private String reportFrom;

    /** Настроен ли отправитель: без ключа письма не уходят вовсе. */
    public boolean configured() {
        return resendApiKey != null && !resendApiKey.isBlank();
    }

    /**
     * Отправить письмо и записать, чем это кончилось.
     *
     * @return строка журнала: её идентификатор и признак отправки
     */
    public Sent send(String kind, String subject, List<String> lines) {
        String to = reportEmail != null && !reportEmail.isBlank() ? reportEmail : properties.owner();
        long emailId = store.queueEmail(kind, to, Json.str(subject, 300));
        if (!configured()) {
            store.emailFailed(emailId, "RESEND_API_KEY не настроен");
            return new Sent(emailId, false);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", reportFrom == null || reportFrom.isBlank() ? DEFAULT_FROM : reportFrom);
            body.put("to", List.of(to));
            body.put("subject", subject);
            body.put("text", String.join("\n", lines));
            Map<String, Object> response = Json.map(webClientBuilder.build().post()
                    .uri(ENDPOINT)
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(TIMEOUT));
            store.emailSent(emailId, Json.str(response.get("id"), 120));
            return new Sent(emailId, true);
        } catch (Exception e) {
            log.warn("Письмо «{}» не ушло: {}", subject, e.getMessage());
            store.emailFailed(emailId, Json.str(e.getMessage(), REASON_LIMIT));
            return new Sent(emailId, false);
        }
    }

    /** Что стало с письмом: строка журнала и признак отправки. */
    public record Sent(long emailId, boolean delivered) {
    }
}
