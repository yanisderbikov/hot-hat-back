package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.game.domain.WeaponType;

/**
 * Оружие в каталоге.
 *
 * <p>Ровно те сведения, по которым клиент рисует панель арсенала и решает,
 * показывать ли кнопку: сколько зарядов даётся, сколько длится эффект, нужен
 * ли режим диверсий. Раньше эта же таблица жила во фронте своей копией
 * ({@code app-core.js:356}) и разошлась с серверной.
 */
@Schema(description = "Вид диверсионного оружия")
public record WeaponView(

        @Schema(description = "Вид оружия", example = "negative")
        WeaponType type,

        @Schema(description = "Ключ боезапаса; пусто у оружия без зарядов", example = "negative",
                nullable = true)
        String ammoKey,

        @Schema(description = "Сколько зарядов выдаётся на партию", example = "1")
        int baseAmmo,

        @Schema(description = "Длительность эффекта в миллисекундах; 0 — берётся из мема, "
                + "-1 — держится до конца хода", example = "10000")
        long durationMs,

        @Schema(description = "Какую дорожку сцены занимает эффект", example = "VIDEO",
                allowableValues = {"VIDEO", "VOICE", "CROCODILE", "OVERLAY", "REPLACEMENT", "NONE"})
        String effectLock,

        @Schema(description = "Только в режиме диверсий и не в тестовой комнате", example = "true")
        boolean advanced,

        @Schema(description = "Только для владельца сервиса", example = "false")
        boolean ownerOnly,

        @Schema(description = "Сколько времени хода должно остаться, иначе эффект не поместится",
                example = "11000")
        long minimumTurnRemainingMs,

        @Schema(description = "Не подчиняется общей перезарядке", example = "false")
        boolean cooldownExempt,

        @Schema(description = "Разрешено во время чужой Подмены", example = "false")
        boolean allowedDuringReplacement) {
}
