package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Снят ли снимок расхода.
 *
 * <p>Плановый снимок берётся четыре раза в сутки
 * ({@code MonitorServiceImpl.scheduledWindowAllowed}); вне этих часов запрос
 * агента не делает ничего. Сегодня это выражается ключами
 * {@code {skipped:true, reason:"scheduled_window"}} — второй формой ответа у
 * той же операции.
 */
@Schema(description = "Снят ли снимок расхода")
public enum UsageSnapshotState {

    /** Снимок снят и сохранён. */
    TAKEN,

    /** Сейчас не час планового снимка: расход не мерили. */
    SKIPPED_OUTSIDE_WINDOW
}
