package ru.hothat.recording.store;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Журнал уведомлений LiveKit Egress.
 *
 * <p>{@link #payload} — второй и последний jsonb на всю базу (§6), и он такой
 * по той же причине, что {@code moderation_action.details}: форму задаёт не
 * наш код. Тело вебхука — чужой контракт, и колонки под него означали бы
 * обещание, что он не пополнится. Четыре значения, которые мы из него ЧИТАЕМ,
 * вынесены в колонки рядом; сырое тело остаётся ради разбора инцидентов.
 *
 * <p>{@link #deliveryId} — идентификатор доставки; при его отсутствии в теле
 * его место занимает хеш тела. Сегодня повтор доставки применяется второй раз,
 * потому что отличить его не по чему: уникальный индекс — и есть починка.
 *
 * <p>{@link #recordingId} пуст, если уведомление не удалось привязать к
 * записи. Строка всё равно пишется: журнал, который молчит о непонятом
 * событии, бесполезен именно тогда, когда нужен.
 */
@Entity
@Table(name = "recording_egress_event", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class EgressEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false, updatable = false, length = 120)
    private String deliveryId;

    @Column(name = "recording_id", updatable = false)
    private UUID recordingId;

    @Column(name = "event_name", nullable = false, updatable = false, length = 60)
    private String eventName;

    @Column(name = "egress_id", updatable = false, length = 120)
    private String egressId;

    /** Сырой код LiveKit: EGRESS_COMPLETE и прочие. Дальше журнала не идёт. */
    @Column(name = "egress_state", updatable = false, length = 40)
    private String egressState;

    /**
     * Уведомление изменило состояние записи. false — повтор, чужая выгрузка
     * или событие не про Egress: EgressWebhookOutcome называет все случаи.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean applied = false;

    @Type(JsonType.class)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Builder.Default
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
