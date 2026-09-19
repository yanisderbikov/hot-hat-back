package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Построчные состояния приглашений.
 *
 * <p>Список, а не карта «идентификатор → состояние», как отвечает старый
 * адрес: карту с произвольными ключами нельзя описать схемой, и в спецификации
 * она выглядела бы объектом без единого свойства. Идентификатор лежит внутри
 * строки.
 *
 * <p>Курсора нет: спрашивающий сам назвал, что его интересует, и страницы
 * здесь взяться неоткуда. Поле объявлено ради общей формы списков и всегда
 * пусто.
 */
@Schema(description = "Состояния приглашений в комнаты")
public record RoomInviteStatusesResponseDTO(

        @Schema(description = "Состояния в том же порядке, в каком спрашивали")
        List<RoomInviteStatusView> items,

        @Schema(description = "Курсор следующей страницы. Всегда null: список задаёт спрашивающий",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Сколько приглашений спросили", example = "12", type = "integer")
        int limit) {
}
