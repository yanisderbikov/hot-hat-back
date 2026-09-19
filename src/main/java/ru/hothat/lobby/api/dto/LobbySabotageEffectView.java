package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Диверсия, которую превью показывает поверх плитки игрока.
 *
 * <p>Помидор и крокодил — единственные два вида, которые превью умеет
 * нарисовать ({@code live-preview.js:78-88}); остальные приезжают сюда тем же
 * полем {@code type} и просто не рисуются. Отбирать их на сервере нельзя:
 * тогда зритель, у которого страница новее, не увидел бы нового эффекта, пока
 * не выкатят сервер.
 */
@Schema(description = "Действующая диверсия в комнате")
public record LobbySabotageEffectView(

        @Schema(description = "Идентификатор события: по нему клиент отличает новую диверсию от той же самой",
                example = "sab-9f31ab77c204")
        String eventId,

        @Schema(description = "Вид диверсии", example = "tomato")
        String type,

        @Schema(description = "В кого прилетело", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk", nullable = true)
        String targetUid,

        @Schema(description = "Когда выпустили, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs,

        @Schema(description = "Сколько миллисекунд эффект держится", example = "8000", type = "integer")
        long durationMs) {
}
