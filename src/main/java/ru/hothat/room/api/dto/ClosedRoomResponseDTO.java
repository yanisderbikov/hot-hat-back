package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог закрытия комнаты хозяином.
 *
 * <p>Заменяет {@code POST /api/cleanup-rooms?room_id=}, который до сих пор
 * звал браузер за хозяина ({@code app-core.js:1048}). Тот адрес не принимал
 * личность вовсе — проверять было нечего (находка A2 аудита), — и любой
 * желающий мог удалить чужую комнату по идентификатору из чата.
 *
 * <p>Ответ различает два исхода одним полем: комната стёрта целиком, если в
 * ней никого не осталось, и просто закрыта, если кто-то ещё сидит — таким
 * людям нужно показать «хозяин закрыл комнату», а не пустой экран.
 */
@Schema(description = "Закрытая комната")
public record ClosedRoomResponseDTO(

        @Schema(description = "Комната", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Комната удалена вместе со всем содержимым; false — только помечена "
                + "закрытой, потому что в ней ещё есть люди", example = "true", type = "boolean")
        boolean deleted,

        @Schema(description = "Когда закрыли, миллисекунды эпохи", example = "1788600000000",
                type = "integer")
        long closedAtMs) {
}
