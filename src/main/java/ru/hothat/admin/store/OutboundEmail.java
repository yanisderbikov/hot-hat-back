package ru.hothat.admin.store;

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

import java.time.Instant;

/**
 * Журнал исходящих писем.
 *
 * <p>Заменяет два jsonb-поля {@code email} (в тревоге и в отчёте) и
 * таблицу-счётчик {@code usage_mail_counter} сразу. Счётчик был выводимым:
 * «сколько писем ушло за сутки» — это число строк журнала за сутки, и держать
 * рядом ещё и счётчик значило дать им шанс разойтись. Ради этого счёта и стоит
 * частичный индекс по {@link #sentAt}.
 *
 * <p>Ключом индекса стоит время ОТПРАВКИ, а не постановки в очередь: между
 * ними лежит сетевой вызов, и на границе суток эти два времени расходятся, а
 * квота отправителя считается по отправке.
 */
@Entity
@Table(name = "outbound_email", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class OutboundEmail {

    /** Зачем письмо; набор закрыт ограничением базы. */
    static final String USAGE_ALERT = "usage_alert";
    static final String USAGE_REPORT = "usage_report";

    /** Что с письмом; набор закрыт ограничением базы. */
    static final String QUEUED = "queued";
    static final String SENT = "sent";
    static final String FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 24)
    private String kind;

    @Column(nullable = false, updatable = false, length = 320)
    private String recipient;

    @Column(nullable = false, updatable = false, length = 300)
    private String subject;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = QUEUED;

    /** Идентификатор письма у отправителя; пусто, пока он не ответил. */
    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 400)
    private String failureReason;

    @Builder.Default
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;
}
