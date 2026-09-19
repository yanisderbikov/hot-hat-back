package ru.hothat.sabotage.store;

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
 * Диверсант партии: перезарядка и курсор выдачи мемов.
 *
 * <p>Строка есть у каждого, кто может стрелять, — ВКЛЮЧАЯ ТЕСТ-БОТА. Это и
 * есть ответ на вопрос, где место колонкам {@code test_bot_*} и jsonb
 * {@code test_bot_runtime}: у бота больше нет отдельного мира внутри строки
 * комнаты, он получает те же строки, что и человек, и отличается одним
 * признаком {@link #bot}. Сегодня правило выдачи мемов написано дважды — для
 * строки игрока и для карты runtime, — и две реализации одного правила уже
 * разошлись.
 *
 * <p>Строка — цель {@code SELECT … FOR UPDATE} на выстреле (§6.4): под ней
 * проверяется перезарядка и лимит трёх подменных клипов.
 */
@Entity
@Table(name = "match_saboteur", schema = "v2")
@IdClass(MatchSaboteurId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchSaboteur {

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /**
     * Пусто — перезарядки нет. Сегодня это {@code bigint} со значением ноль,
     * который невозможно отличить от «перезарядка кончилась в 1970 году».
     */
    @Column(name = "cooldown_until")
    private Instant cooldownUntil;

    /** Куда встал круг выдачи, когда кончились и резерв, и переработка. */
    @Builder.Default
    @Column(name = "meme_cycle_cursor", nullable = false)
    private Integer memeCycleCursor = 0;

    @Builder.Default
    @Column(name = "is_bot", nullable = false, updatable = false)
    private Boolean bot = false;
}
