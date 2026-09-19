package ru.hothat.game.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Окно апелляции по ходу: строка есть — окно открыто.
 *
 * <p>Числа голосующих и большинства колонками не заведены, хотя ответ их
 * отдаёт: и то и другое считается по ростеру партии («все, кроме команды,
 * которая ходила»), а хранить рядом со списком его же размер — это счётчик,
 * который однажды разойдётся со списком.
 *
 * <p>Строка берётся {@code SELECT … FOR UPDATE} при закрытии (§6.4): иначе
 * сторож и последний проголосовавший закроют окно дважды и дважды применят
 * отмену слов.
 */
@Entity
@Table(name = "match_appeal", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchAppeal {

    @Id
    @Column(name = "turn_id", nullable = false, updatable = false)
    private UUID turnId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** Пусто — окно ещё открыто. */
    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
