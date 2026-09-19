package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Совпала ли партия, которую снимает рекордер, с текущей партией комнаты.
 *
 * <p>Сегодня расхождение выражается ключом {@code staleGame:true} в ответе
 * ({@code RecorderStateServiceImpl.staleGame}), а рядом приезжает выдуманная
 * фаза {@code finished} — рекордер обязан догадаться по наличию ключа, что
 * снимать больше нечего. Здесь это одно поле-перечисление, и форма ответа
 * остаётся одна.
 */
@Schema(description = "Состояние сцены относительно текущей партии комнаты")
public enum RecorderSceneState {

    /** Номер партии совпал: сцена настоящая. */
    LIVE,

    /** Комната ушла вперёд: снимать эту партию больше нечего. */
    STALE_GAME
}
