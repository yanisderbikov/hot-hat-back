package ru.hothat.admin.store;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * След действия модератора: кто, что и над чем сделал.
 *
 * <p>Сегодня следа нет вовсе: снятие бана прямо пишет в журнал приложения, что
 * автора решения сохранить некуда.
 *
 * <p>Отсюда же берутся «сигналы на мемы». Отдельной таблицы {@code meme_report}
 * в плане нет намеренно (§6.3): жалоб на мемы не существует ни в одной
 * операции аудита, а {@code delete_meme_alert} означает «админ снял мем с
 * публикации». Это действие модератора, и здесь оно — строка с
 * {@link #subjectType} = {@code meme}.
 *
 * <p>{@link #details} — один из двух jsonb на всю базу, и он такой не по лени:
 * набор полей зависит от вида действия (у снятия мема — причина и слаг, у
 * закрытия комнаты — сколько игроков выставили), и колонок под объединение
 * всех видов было бы больше, чем самих видов.
 *
 * <p>{@link #actorPlayerId} без внешнего ключа: журнал обязан пережить
 * удаление учётки модератора (см. шапку миграции V13).
 */
@Entity
@Table(name = "moderation_action", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ModerationAction {

    /** Над чем совершено действие; набор закрыт ограничением базы. */
    static final String PLAYER = "player";
    static final String ROOM = "room";
    static final String MEME = "meme";
    static final String RECORDING = "recording";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_player_id", nullable = false, updatable = false)
    private UUID actorPlayerId;

    @Column(nullable = false, updatable = false, length = 40)
    private String action;

    @Column(name = "subject_type", nullable = false, updatable = false, length = 16)
    private String subjectType;

    /**
     * Строкой, а не uuid: у комнаты ключ {@code hat-<hex16>}, у мема —
     * {@code meme-…}, у игрока — uuid. Приводить их к одному типу нечем.
     */
    @Column(name = "subject_id", nullable = false, updatable = false, length = 180)
    private String subjectId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
