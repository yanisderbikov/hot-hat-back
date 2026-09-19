package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница реестра учётных записей.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=list_users}, который
 * лежал под общим матчером {@code authenticated} и отдавал почту всех
 * аккаунтов любому вошедшему, у кого хватило догадливости позвать это действие
 * (A6). Теперь адрес требует владельца, а страница ограничена сотней.
 */
@Schema(description = "Страница реестра учётных записей")
public record AdminUserPageResponseDTO(

        @Schema(description = "Учётки, недавно зарегистрированные сверху; гости и свой аккаунт "
                + "в список не попадают")
        List<AdminUserCardView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт "
                + "первую страницу и не умеет продолжать", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "100", type = "integer")
        int limit) {
}
