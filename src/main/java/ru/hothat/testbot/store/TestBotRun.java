package ru.hothat.testbot.store;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Прогон тестовых ботов в комнате.
 *
 * <p>Третий адрес из разбора семнадцати колонок про тест-ботов, обещанный
 * хвостом V15: признак «это бот» остался в кластере комнаты, состояние бота
 * как игрока — в тех же строках, что у человека, а сам прогон, его владелец и
 * его часы живут здесь.
 *
 * <p>{@link #ownerPlayerId} — владелец сервиса, запустивший прогон. Сегодня
 * это {@code room.test_owner_uid}, и при пустом значении право молча падало
 * обратно на {@code room.created_by} — то есть на колонку, которую
 * переписывает передача хозяйства комнаты. Здесь владелец прогона отдельный и
 * неизменный.
 *
 * <p>{@link #ownerExplainerGameNumber} и {@link #ownerExplainerTurnNumber} —
 * та самая пара, которой сценарий отличает первые два хода владельца (их ведёт
 * он сам) от последующих (их ведут боты). Она про прогон, а не про комнату,
 * поэтому и переехала сюда.
 *
 * <p>Уникальный частичный индекс «один идущий прогон на комнату»: сегодня два
 * запуска подряд заводили вторую обойму ботов поверх первой, и остановка
 * гасила только последнюю.
 */
@Entity
@Table(name = "test_bot_run", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TestBotRun {

    /** Состояние прогона; набор закрыт ограничением базы. */
    static final String RUNNING = "running";
    static final String STOPPED = "stopped";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Column(name = "owner_player_id", nullable = false, updatable = false)
    private UUID ownerPlayerId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = RUNNING;

    @Builder.Default
    @Column(name = "bot_count", nullable = false)
    private Short botCount = 0;

    @Builder.Default
    @Column(name = "owner_explainer_game_number", nullable = false)
    private Integer ownerExplainerGameNumber = 0;

    @Builder.Default
    @Column(name = "owner_explainer_turn_number", nullable = false)
    private Integer ownerExplainerTurnNumber = 0;

    @Builder.Default
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "stopped_at")
    private Instant stoppedAt;
}
