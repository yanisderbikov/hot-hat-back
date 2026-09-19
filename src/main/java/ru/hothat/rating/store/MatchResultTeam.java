package ru.hothat.rating.store;

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
 * Разбор зачёта по командам: место, начисление, виновность.
 *
 * <p>Заменяет jsonb {@code culprit_team_ids} и восстанавливает то, чего
 * сегодня нет вовсе: сколько именно очков команда получила за эту партию.
 * Сейчас об этом можно судить только по разнице в итоговой таблице, то есть
 * уже никак — таблица меняется дальше.
 *
 * <p>{@link #place} пусто у виновной команды: места она не занимает, ей
 * начисляется штраф. База проверяет согласие этих двух полей.
 */
@Entity
@Table(name = "match_result_team", schema = "v2")
@IdClass(MatchResultTeamId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchResultTeam {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Id
    @Column(name = "game_number", nullable = false, updatable = false)
    private Integer gameNumber;

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "place")
    private Integer place;

    /** Со знаком: у виновной команды это штраф 15/30/50 с минусом. */
    @Builder.Default
    @Column(name = "points_delta", nullable = false)
    private Integer pointsDelta = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean culprit = Boolean.FALSE;
}
