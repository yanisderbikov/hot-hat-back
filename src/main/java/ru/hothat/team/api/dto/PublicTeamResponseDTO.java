package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

import java.util.List;

/**
 * Публичная карточка чужой команды: её открывают из таблицы рейтингов.
 *
 * <p>Заменяет {@code POST /api/portal} с действием {@code public_team_profile}.
 * Там ответ был вложен на четыре уровня — команда, внутри неё участники, внутри
 * каждого его собственная команда, внутри неё её рейтинги, — и стоил трёх
 * чтений на участника. Здесь участник плоский: чтобы посмотреть его команду и
 * его рейтинги, есть его собственный публичный профиль.
 *
 * <p>Своей команды тут не видно ничего лишнего: ни приглашений, ни префлайта,
 * ни идентификатора лобби. Адрес открыт любому вошедшему игроку.
 */
@Schema(description = "Публичная карточка команды")
public record PublicTeamResponseDTO(

        @Schema(description = "Идентификатор команды", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String id,

        @Schema(description = "Название команды", example = "Hat Wolves")
        String name,

        @Schema(description = "Логотип как data-URL; null — логотипа нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", nullable = true)
        String logoDataUrl,

        @Schema(description = "Дивизион команды")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Подтверждена ли команда напарником. Неподтверждённая уже видна по "
                + "ссылке, но в таблицах сезона её нет")
        TeamStatus status,

        @Schema(description = "Оба участника в порядке основания")
        List<PublicTeamMemberView> members,

        @Schema(description = "Показатели по режимам за текущий сезон")
        TeamStatsView stats) {
}
