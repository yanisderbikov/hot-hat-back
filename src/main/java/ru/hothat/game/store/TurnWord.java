package ru.hothat.game.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Исход слова в ходе: засчитано или пропущено.
 *
 * <p>Заменяет jsonb {@code turn_guessed_words}, где пропущенные слова лежали
 * в том же массиве с флагом.
 *
 * <p><b>Ключ здесь составной, и это осознанное отступление от §6.2 плана,</b>
 * который называет ключом один лишь {@code id}. Причина: {@code id}
 * придумывает КЛИЕНТ и присылает в адресе операции (шаблон
 * {@code ^[A-Za-z0-9_-]{6,80}$} в контроллере хода) — это ключ идемпотентности
 * нажатия. Шести знаков достаточно, чтобы два клиента в разных комнатах
 * выбрали одну строку, и при глобальном ключе такое совпадение дало бы не
 * ошибку, а худшее: сценарий счёл бы чужую запись повтором и не начислил
 * очко. Ключ {@code (turn_id, id)} делает идемпотентность ровно такой, какая
 * нужна, — в пределах хода.
 *
 * <p>{@link #invalidated} вместо удаления строки: слово, отменённое
 * апелляцией, обязано остаться видимым в разборе хода — иначе непонятно, за
 * что отняли очко.
 *
 * <p>{@code @Version} нет намеренно (§6.4): строку пишет сценарий, уже
 * держащий блокировку хода.
 */
@Entity
@Table(name = "match_turn_word", schema = "v2")
@IdClass(TurnWordId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TurnWord {

    /** guessed | skipped — набор закрыт ограничением базы. */
    static final String GUESSED = "guessed";
    static final String SKIPPED = "skipped";

    @Id
    @Column(name = "turn_id", nullable = false, updatable = false)
    private UUID turnId;

    /** Ключ идемпотентности нажатия, выбранный клиентом. */
    @Id
    @Column(nullable = false, updatable = false, length = 80)
    private String id;

    @Column(name = "match_word_id", nullable = false, updatable = false)
    private UUID matchWordId;

    @Column(nullable = false, updatable = false, length = 8)
    private String outcome;

    /** Порядок внутри хода: по нему собирается разбор и считаются награды. */
    @Column(nullable = false, updatable = false)
    private Integer ordinal;

    @Builder.Default
    @Column(nullable = false)
    private Boolean invalidated = false;

    @Builder.Default
    @Column(name = "resolved_at", nullable = false, updatable = false)
    private Instant resolvedAt = Instant.now();
}
