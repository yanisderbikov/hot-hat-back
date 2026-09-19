package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Итог планового прохода по брошенным комнатам.
 *
 * <p>Заменяет {@code GET и POST /api/cleanup-rooms?sweep=1}. Точечная уборка
 * одной комнаты по {@code room_id} сюда не переехала: её звал клиент при выходе
 * из комнаты, и это работа области комнаты, а не планировщика. Заодно исчезает
 * ранний выход {@code NoRoomRequested}, из-за которого запрос без {@code room_id}
 * отвечал «всё хорошо», не сделав ничего.
 */
@Schema(description = "Итог уборки брошенных комнат")
public record RoomSweepResponseDTO(

        @Schema(description = "Состоялся ли прогон")
        SweepState state,

        @Schema(description = "Сколько комнат просмотрено", example = "42", type = "integer")
        int checkedCount,

        @Schema(description = "Сколько комнат снесено", example = "3", type = "integer")
        int deletedCount,

        @Schema(description = "Какие именно комнаты снесены")
        List<SweptRoomView> rooms) {
}
