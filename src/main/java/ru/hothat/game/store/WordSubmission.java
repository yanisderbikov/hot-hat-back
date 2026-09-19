package ru.hothat.game.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Слово, сданное в шляпу до старта партии.
 *
 * <p>Сегодня это {@code room_word_submission}: одна строка на игрока с
 * jsonb-массивом слов внутри и счётчиком рядом. Из-за массива «убрать одно
 * своё слово» — это переписать весь список, а два одновременных пополнения
 * теряют одно из них целиком.
 *
 * <p>{@link #normalized} считает база выражением {@code lower(btrim(word))} —
 * тем же, что и ключ ника в V13. Поэтому «Кот», «кот&nbsp;» и «кот» — одно
 * слово, и узнаёт это уникальный индекс, а не чтение перед вставкой. Поле
 * только читается.
 *
 * <p>{@code @Version} нет намеренно (§6.4): каждый пишет только свои строки,
 * а повтор отвергает уникальный индекс.
 */
@Entity
@Table(name = "word_submission", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class WordSubmission {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Column(name = "author_player_id", nullable = false, updatable = false)
    private UUID authorPlayerId;

    /** 80 знаков — предел, который проверяет DTO на входе. */
    @Column(nullable = false, length = 80)
    private String word;

    @Column(insertable = false, updatable = false, length = 80)
    private String normalized;

    @Builder.Default
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();
}
