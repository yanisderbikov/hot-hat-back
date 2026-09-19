package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.hothat.admin.domain.HealthProbe;
import ru.hothat.util.Json;

import java.time.Clock;
import java.time.Duration;

/**
 * Проверка живости по сети: жив ли сайт, жив ли наш API.
 *
 * <p>Таймаут семь секунд — столько же, сколько было. Он важнее, чем кажется:
 * проверка идёт внутри сбора снимка, а сбор запускает человек кнопкой и ждёт
 * ответа. Без потолка недоступный сайт держал бы кнопку минутами.
 *
 * <p>Ошибка — это ответ «не отвечает», а не исключение наружу: снимок обязан
 * записать состояние служб, включая упавшие. Ради этого проверка и хранится.
 */
@Component
@RequiredArgsConstructor
public class LivenessProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(7);
    private static final String AGENT = "HOT-HAT-Monitor/1.0";
    /** Столько знаков сообщения об ошибке влезает в колонку {@code detail}. */
    private static final int DETAIL_LIMIT = 200;

    private final WebClient.Builder webClientBuilder;
    private final Clock clock;

    /** Позвать адрес и замерить, сколько он отвечал. */
    public HealthProbe ping(String key, String label, String url) {
        long started = clock.millis();
        try {
            webClientBuilder.build().get().uri(url)
                    .header("User-Agent", AGENT)
                    .retrieve().toBodilessEntity().block(TIMEOUT);
            return HealthProbe.up(key, label, "Доступен").took(clock.millis() - started);
        } catch (Exception e) {
            return HealthProbe.down(key, label, Json.str(e.getMessage(), DETAIL_LIMIT))
                    .took(clock.millis() - started);
        }
    }
}
