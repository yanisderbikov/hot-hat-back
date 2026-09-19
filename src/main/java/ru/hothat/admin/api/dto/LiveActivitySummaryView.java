package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Четыре счётчика «прямо сейчас», которые админка рисует над списком комнат.
 *
 * <p>Едут вместе со страницей комнат, а не отдельным адресом, и это решение.
 * Три счётчика из четырёх — производные от той же выборки: пересчитать их
 * вторым запросом значит прочитать все комнаты и всех игроков ещё раз ради
 * четырёх чисел.
 */
@Schema(description = "Счётчики текущей нагрузки")
public record LiveActivitySummaryView(

        @Schema(description = "Сколько разных игроков сейчас активны в комнатах", example = "17",
                type = "integer")
        int onlineUsers,

        @Schema(description = "Сколько комнат, в которых есть хотя бы один активный игрок",
                example = "4", type = "integer")
        int activeRooms,

        @Schema(description = "Сколько незакрытых комнат всего, включая пустые", example = "9",
                type = "integer")
        int openRooms,

        @Schema(description = "Сколько учётных записей заведено за всё время", example = "1284",
                type = "integer")
        long registeredUsers) {
}
