package ru.hothat.app.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.app.api.dto.AnalyticsEventAcceptedResponseDTO;
import ru.hothat.app.api.dto.AnalyticsEventPayloadView;
import ru.hothat.app.api.dto.RecordAnalyticsEventRequestDTO;
import ru.hothat.app.store.AnalyticsStore;
import ru.hothat.config.HotHatUser;

/**
 * Принять событие клиентской аналитики.
 *
 * <p>Только для вошедших, и это прочитано буквально из решения о госте:
 * гостю открыты лобби, превью, апгрейд и согласия — аналитики среди них нет.
 * Событие без личности всё равно нечего класть в отчёт: все шесть видов
 * событий считаются по игрокам.
 *
 * <p>Идемпотентность по {@code eventKey} проверяется в хранилище: обработчик
 * конца партии срабатывает у каждого участника, и одно событие приезжает на
 * сервер пять раз. Ответ у первого и у повтора один — см.
 * {@link AnalyticsEventAcceptedResponseDTO}.
 *
 * <p>Границы полей проверены на DTO и не прижимаются здесь к краю: значение
 * вне границ отвергается запросом, а не молча искажает отчёт.
 */
@Service
@RequiredArgsConstructor
public class RecordAnalyticsEventUseCase {

    private final AnalyticsStore analytics;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public AnalyticsEventAcceptedResponseDTO run(HotHatUser user, RecordAnalyticsEventRequestDTO request) {
        AnalyticsEventPayloadView payload = request.payload();
        analytics.record(user.uid(), new AnalyticsStore.Event(
                request.eventType().wireValue(),
                request.eventKey(),
                request.roomId(),
                payload == null ? null : payload.roomName(),
                shortValue(payload == null ? null : payload.playerCount()),
                shortValue(payload == null ? null : payload.teamCount()),
                payload == null ? null : payload.wordCount(),
                payload == null ? null : payload.gameNumber(),
                payload == null ? null : payload.durationSeconds()));
        return new AnalyticsEventAcceptedResponseDTO(request.eventKey());
    }

    /**
     * Счётчики игроков и команд лежат в колонках smallint: их потолки — 10 и 5,
     * и брать под них четыре байта незачем. Проверка границ уже прошла на DTO,
     * поэтому сужение здесь ничего не теряет.
     */
    private static Short shortValue(Integer value) {
        return value == null ? null : value.shortValue();
    }
}
