package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.lobby.api.dto.LobbyRoomCardView;

import java.util.List;

/**
 * Витрина открытых комнат — то, что канал показывает прямо сейчас.
 *
 * <p>Общая проекция обоих кадров канала: приветственного и обновляющего.
 * Форма описана один раз, кадры различаются только именем.
 *
 * <p>Поля повторяют {@code LobbyRoomPageResponseDTO} один в один, а карточка —
 * тот же {@link LobbyRoomCardView}, что отдаёт {@code GET /api/v2/lobby/rooms}.
 * Это главное свойство канала: главная получает список одной формы, откуда бы
 * он ни приехал. Прежняя подписка присылала сырые документы комнат — вместе с
 * мешком неразыгранных слов и текущим словом чужой команды, — и рисовались они
 * не тем же кодом, что ответ HTTP.
 *
 * <p>Снимок целиком, а не дельта: витрина короткая, а перечитывание надёжнее
 * склейки приращений и не расходится с базой.
 */
@Schema(description = "Витрина открытых комнат")
public record LobbyRoomsWindowView(

        @Schema(description = "Комнаты, в которые сейчас можно войти или которые можно смотреть")
        List<LobbyRoomCardView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — витрина приезжает "
                + "одним куском", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой витрине", example = "50", type = "integer")
        int limit) {
}
