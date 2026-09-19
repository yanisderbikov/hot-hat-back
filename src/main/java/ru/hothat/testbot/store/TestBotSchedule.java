package ru.hothat.testbot.store;

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

/**
 * Часы прогона ботов.
 *
 * <p>Три колонки {@code next_*_at} жили в строке комнаты на 103 колонки, и тик
 * прогона — он идёт примерно раз в секунду — переписывал вместе с ними
 * настройки, составы, приглашения и мешок слов. Здесь их пять, и у таблицы
 * стоит fillfactor 70: обновление проходит внутри страницы (HOT-update) и не
 * трогает индексы — тот же приём, что у {@code v2.player_presence} в V13 и
 * {@code v2.room_presence} в V15.
 *
 * <p>Индекса по {@link #nextActionAt} нет намеренно, хотя §6.2 его называет:
 * тик сегодня запускает КЛИЕНТ, обращаясь к своей комнате, то есть строка
 * читается по первичному ключу. Серверного планировщика, которому нужен был бы
 * список «кому пора ходить», не существует, а появись он — индекс по вечно
 * меняющейся колонке отменил бы HOT-обновление, ради которого таблица и
 * отделена от комнаты.
 *
 * <p>{@link #specialEffectUntil} и {@link #specialEffectCursor} — одна пара
 * вместо двух: сегодня рядом лежит {@code test_bot_voice_effect_*} с тем же
 * смыслом, и обновляется она, как прямо написано в коде, «ради совместимости с
 * прежними клиентами» (§3.2, §6.3).
 *
 * <p>{@code @Version} (§6.4): тик и остановка прогона пишут одну строку, и
 * остановка не должна потеряться под тиком, начавшимся раньше неё.
 */
@Entity
@Table(name = "test_bot_schedule", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TestBotSchedule {

    @Id
    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    @Column(name = "next_sabotage_at")
    private Instant nextSabotageAt;

    @Column(name = "next_chat_at")
    private Instant nextChatAt;

    @Column(name = "special_effect_until")
    private Instant specialEffectUntil;

    @Builder.Default
    @Column(name = "special_effect_cursor", nullable = false)
    private Integer specialEffectCursor = 0;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
