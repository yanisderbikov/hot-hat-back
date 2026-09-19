package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

/**
 * Напарник — второй участник команды, не считая самого игрока.
 *
 * <p>Отдельным полем рядом с составом, а не вместо него: экран команды и
 * страница друзей подписывают напарника поимённо («Чат с Петей доступен
 * здесь»), и вычислять его на клиенте вычитанием себя из состава значит
 * повторять один и тот же фильтр в трёх местах разметки.
 *
 * <p>null, когда напарника ещё нет: у только что основанной команды в составе
 * уже двое, но приглашение может быть отклонено, и до ответа второй участник —
 * приглашённый, а не напарник.
 */
@Schema(description = "Напарник по команде")
public record TeamPartnerView(

        @Schema(description = "Идентификатор напарника", example = "Zt7bNqXm2wLpK9sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Ник напарника", example = "petya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Дивизион напарника: у подтверждённой команды совпадает с дивизионом команды")
        DivisionLanguage divisionLanguage) {
}
