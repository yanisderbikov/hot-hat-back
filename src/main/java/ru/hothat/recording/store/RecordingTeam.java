package ru.hothat.recording.store;

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
 * Команда записанной партии со счётом и признаком победы.
 *
 * <p>Победа — признак у команды, а не два параллельных массива
 * {@code winner_team_ids} и {@code winner_team_names} рядом со списком команд.
 * Массивы приходилось сшивать по идентификатору на клиенте, и при ничьей —
 * а она бывает — сшивка расходилась.
 *
 * <p>Состава списком здесь нет: он выводится из
 * {@link RecordingParticipant#getRoomTeamId()}.
 */
@Entity
@Table(name = "recording_team", schema = "v2")
@IdClass(RecordingTeamId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RecordingTeam {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Id
    @Column(name = "room_team_id", nullable = false, updatable = false)
    private UUID roomTeamId;

    @Column(nullable = false, updatable = false, length = 40)
    private String name;

    /**
     * Рейтинговая команда, которой играл этот состав; пусто — партия не
     * рейтинговая. Без ключа: роспуск команды не должен трогать запись.
     */
    @Column(name = "ranked_team_id", updatable = false)
    private UUID rankedTeamId;

    @Builder.Default
    @Column(nullable = false)
    private Integer score = 0;

    @Builder.Default
    @Column(name = "is_winner", nullable = false)
    private Boolean winner = false;
}
