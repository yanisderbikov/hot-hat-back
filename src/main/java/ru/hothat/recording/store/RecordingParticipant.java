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
 * Кто играл в записанной партии.
 *
 * <p>Заменяет два jsonb сразу: {@code participants} (список объектов) и
 * {@code participant_uids} (те же uid списком строк, заведённые только ради
 * поиска «я участник» нативным запросом с оператором {@code @>}).
 *
 * <p>{@link #nickname} — снимок, а не ссылка на профиль, и это та копия чужих
 * данных, которую §6.3 оставляет сознательно: игрок мог сменить ник после
 * партии, а на видео звучит и написан старый. Показывать под записью
 * сегодняшний ник значило бы спорить с самим видео.
 *
 * <p>Ключа на учётку нет: в записанной партии мог играть тест-бот, а у бота
 * учётки нет и не будет.
 */
@Entity
@Table(name = "recording_participant", schema = "v2")
@IdClass(RecordingParticipantId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RecordingParticipant {

    @Id
    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Column(nullable = false, updatable = false, length = 20)
    private String nickname;

    /** Команда, за которую играл; пусто — остался без команды. */
    @Column(name = "room_team_id", updatable = false)
    private UUID roomTeamId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Boolean bot = false;
}
