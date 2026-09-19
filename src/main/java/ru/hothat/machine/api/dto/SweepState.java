package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состоялся ли прогон уборки.
 *
 * <p>У прохода по комнатам есть кулдаун в пять минут
 * ({@code RoomCleanupServiceImpl.claimSweep}): он защищает от нескольких
 * одновременных проходов. Сегодня отказ приезжает ключом {@code skipped:true}
 * внутри вложенного объекта, и внешний планировщик о нём не знает.
 */
@Schema(description = "Состоялся ли прогон уборки")
public enum SweepState {

    /** Прогон выполнен. */
    EXECUTED,

    /** Прогон пропущен: предыдущий был меньше кулдауна назад. */
    SKIPPED_COOLDOWN
}
