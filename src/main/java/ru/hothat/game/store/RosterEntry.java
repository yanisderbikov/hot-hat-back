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
 * Замороженный на старте ростер партии.
 *
 * <p>Заменяет два jsonb сразу: {@code team_rosters} (составы) и
 * {@code game_player_names_by_uid} (имена). Партия играется теми, кто был в
 * ней на старте: зашедший позже зритель игроком не становится, а вышедший
 * игрок остаётся в протоколе.
 *
 * <p>Имя класса не {@code MatchPlayer}: так называется класс движка
 * {@code ru.hothat.game.domain.MatchPlayer}, и совпадение простых имён в
 * соседних пакетах одной области читалось бы как ошибка.
 *
 * <p>{@link #displayName} — снимок, и это не та копия чужих данных, которую
 * убирает §6.3. Разница простая: {@code room_player.name} был копией ЖИВОГО
 * ника и обязан был обновляться при его смене (отсюда обход всех комнат
 * игрока на каждое переименование), а здесь имя заморожено намеренно — оно
 * должно остаться прежним, даже когда человек сменит ник или удалит учётку.
 *
 * <p>{@code @Version} нет: ростер пишется один раз и больше не меняется.
 */
@Entity
@Table(name = "match_player", schema = "v2")
@IdClass(RosterEntryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RosterEntry {

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Column(name = "room_team_id", nullable = false, updatable = false)
    private UUID roomTeamId;

    @Column(name = "display_name", nullable = false, updatable = false, length = 40)
    private String displayName;

    /** Место внутри команды: по нему считается, кто объясняет, а кто угадывает. */
    @Column(name = "seat_in_team", nullable = false, updatable = false)
    private Integer seatInTeam;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Boolean bot = false;
}
