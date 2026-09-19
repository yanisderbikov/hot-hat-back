package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог выхода из комнаты.
 *
 * <p>Заменяет пару вызовов, которые до сих пор делал браузер подряд: удаление
 * своего места и {@code POST /api/cleanup-rooms?room_id=} следом
 * ({@code app-core.js:12290}, {@code :1045}). Второй вызов был необязательным
 * — вкладка, закрытая мимо обработчика, его не делала, — и брошенная комната
 * доживала до общего прохода уборщика. Здесь уборка опустевшей комнаты —
 * инвариант самого выхода, а не вежливость клиента.
 *
 * <p>{@code roomClosed} нужен уходящему: если комната исчезла вместе с ним,
 * возвращаться по ссылке некуда, и экран сразу ведёт на главную вместо того,
 * чтобы показать «комната не найдена».
 */
@Schema(description = "Выход из комнаты")
public record LeftRoomResponseDTO(

        @Schema(description = "Комната, из которой вышли", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Комната опустела и была убрана", example = "false", type = "boolean")
        boolean roomClosed,

        @Schema(description = "Команда, из которой заодно вышел игрок; null — он в ней не состоял",
                example = "team-1", nullable = true)
        String leftTeamId) {
}
