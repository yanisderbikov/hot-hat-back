package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Сводка личных переписок игрока.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=social_threads}.
 * Список — объект с курсором, а не голый массив: добавить в ответ ещё одно
 * поле (сегодня это {@code totalUnread}) можно, не ломая разбор у клиента.
 */
@Schema(description = "Список переписок и общее число непрочитанного")
public record ChatThreadsResponseDTO(

        @Schema(description = "Переписки, самые свежие сверху")
        List<ChatThreadView> items,

        @Schema(description = "Курсор следующей страницы. Всегда null: переходный движок отдаёт "
                + "переписки первой сотни друзей одним куском и страниц не знает",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Сколько переписок сервер готов вернуть за раз", example = "100", type = "integer")
        int limit,

        @Schema(description = "Сумма непрочитанного по всем перепискам: значок на кнопке чата",
                example = "5", type = "integer")
        int totalUnread) {
}
