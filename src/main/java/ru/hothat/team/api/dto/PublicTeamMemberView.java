package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

/**
 * Участник чужой команды в объёме публичной карточки.
 *
 * <p>Своя, а не общая с {@link TeamMemberView}: там ник берётся из копии,
 * записанной в команду, здесь — только из профиля, и добавлен дивизион. Но
 * главное — раньше каждый участник публичного профиля был целой карточкой
 * игрока со своей командой и своими рейтингами, то есть тем же профилем на
 * четвёртом уровне вложенности. Экран из всего этого рисовал аватар и имя.
 */
@Schema(description = "Участник чужой команды")
public record PublicTeamMemberView(

        @Schema(description = "Идентификатор игрока: по нему открывается его публичный профиль",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Ник игрока", example = "vasya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String avatarDataUrl,

        @Schema(description = "Дивизион игрока")
        DivisionLanguage divisionLanguage) {
}
