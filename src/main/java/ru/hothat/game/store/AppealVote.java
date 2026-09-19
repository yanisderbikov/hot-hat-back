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
 * Голос за отмену слова.
 *
 * <p>Заменяет jsonb {@code appeal_votes} — карту «слово → список
 * проголосовавших» в строке комнаты. Два одновременных голоса читали одну
 * карту и записывали две: второй затирал первого, и голос молча пропадал.
 * Здесь голос — строка, а повторный голос того же игрока отвергает первичный
 * ключ.
 *
 * <p>{@code @Version} нет намеренно (§6.4): каждый пишет только свою строку.
 */
@Entity
@Table(name = "match_appeal_vote", schema = "v2")
@IdClass(AppealVoteId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AppealVote {

    @Id
    @Column(name = "turn_id", nullable = false, updatable = false)
    private UUID turnId;

    @Id
    @Column(name = "word_id", nullable = false, updatable = false, length = 80)
    private String wordId;

    @Id
    @Column(name = "voter_player_id", nullable = false, updatable = false)
    private UUID voterPlayerId;

    @Builder.Default
    @Column(name = "cast_at", nullable = false, updatable = false)
    private Instant castAt = Instant.now();
}
