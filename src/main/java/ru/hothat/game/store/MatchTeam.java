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

import java.util.UUID;

/**
 * Команда партии и её место в очереди ходов.
 *
 * <p>Очередь — колонка {@link #turnOrder}, а не массив {@code team_order} в
 * строке комнаты. Уникальность порядка внутри партии делает невозможной «две
 * команды на третьем месте», которую массив допускал.
 *
 * <p>Счёта здесь нет: он считается по строкам исходов слов. Денормализованный
 * счётчик рядом с ними однажды разошёлся бы с ними.
 *
 * <p>{@code @Version} нет: строка пишется один раз, на старте партии.
 */
@Entity
@Table(name = "match_team", schema = "v2")
@IdClass(MatchTeamId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchTeam {

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "room_team_id", nullable = false, updatable = false)
    private UUID roomTeamId;

    @Column(name = "turn_order", nullable = false)
    private Integer turnOrder;
}
