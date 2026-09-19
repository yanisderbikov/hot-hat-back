package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Журнал исходящих писем в таблице {@code v2.outbound_email}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}.
 *
 * <p>Счётчика отправленных здесь нет и не будет: «сколько писем ушло за сутки»
 * — это число строк журнала за сутки. Раньше рядом жила таблица-счётчик
 * {@code usage_mail_counter}, и разойтись с журналом ей мешало только везение.
 * Ради этого счёта и стоит частичный индекс {@code ix_outbound_email_sent}.
 */
@Repository
interface OutboundEmails extends JpaRepository<OutboundEmail, Long> {

    long countByStateAndSentAtBetween(String state, Instant from, Instant to);
}
