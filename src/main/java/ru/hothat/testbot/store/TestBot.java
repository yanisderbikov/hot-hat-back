package ru.hothat.testbot.store;

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
 * Один бот прогона.
 *
 * <p>{@link #botPlayerId} — синтетический uuid, и он такой ради одного:
 * со своим uuid строки бота ничем не отличаются от строк человека, поэтому
 * правила партии не приходится писать дважды. Сегодня идентификатор бота
 * выглядит как {@code testbot-<комната>-<номер>}, и внешнего ключа на учётку у
 * него нет и не будет — учётки у бота не бывает.
 *
 * <p>{@link #displayName} живёт здесь, а не в справочнике игроков: у бота нет
 * карточки, а {@code ProfileDirectoryPort} отвечает только за учётки. Комнате
 * имя отдаёт порт этой области — это и есть замена выброшенному
 * {@code room_player.name}.
 *
 * <p>{@link #lastShotAt} — единственный ключ, доехавший сюда из одиннадцати,
 * что лежали в jsonb {@code test_bot_runtime}. Индекса по нему намеренно нет:
 * у прогона не больше девяти ботов, «кто дольше всех не стрелял» отбирается по
 * префиксу первичного ключа, а индекс по этой колонке запретил бы
 * HOT-обновление отметки выстрела — ради него у таблицы и стоит fillfactor 80.
 */
@Entity
@Table(name = "test_bot", schema = "v2")
@IdClass(TestBotId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TestBot {

    @Id
    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Id
    @Column(name = "bot_player_id", nullable = false, updatable = false)
    private UUID botPlayerId;

    @Column(name = "slot_index", nullable = false, updatable = false)
    private Short slotIndex;

    @Column(name = "display_name", nullable = false, length = 20)
    private String displayName;

    @Column(name = "last_shot_at")
    private Instant lastShotAt;
}
