package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница витрины открытых комнат.
 *
 * <p>Заменяет живую подписку {@code query(rooms, where phase in […])}
 * ({@code home/home.js:93}), которая привозила браузеру документ каждой
 * комнаты целиком — вместе с мешком неразыгранных слов, составами, голосами
 * апелляции и текущим словом чужой команды.
 *
 * <p>Числа игроков онлайн здесь нет намеренно: его отдаёт
 * {@code GET /api/v2/profile/presence/summary}, и считается оно по всем
 * игрокам, а не по комнатам витрины. Свести их в один ответ значило бы
 * связать частоту обновления списка комнат с частотой опроса присутствия.
 */
@Schema(description = "Витрина открытых комнат")
public record LobbyRoomPageResponseDTO(

        @Schema(description = "Комнаты, в которые сейчас можно войти или которые можно смотреть")
        List<LobbyRoomCardView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — переходный движок "
                + "отдаёт витрину одним куском", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "50", type = "integer")
        int limit) {
}
