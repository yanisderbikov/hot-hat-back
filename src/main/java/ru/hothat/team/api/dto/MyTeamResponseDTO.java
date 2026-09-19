package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

/**
 * Своя команда: состав, напарник и показатели сезона.
 *
 * <p>Заменяет часть {@code POST /api/portal} с действием {@code my_team}, где
 * одним куском ехали команда, напарник, показатели, снимок префлайта и список
 * приглашений — пять разных предметов с пятью разными частотами обновления.
 * Префлайт живёт под {@code /api/v2/team/me/preflight} и опрашивается раз в
 * полторы секунды, приглашения — под {@code /api/v2/team/invites}; здесь
 * остался только состав, который не меняется неделями.
 *
 * <p>Команды может не быть вовсе, и это не ошибка: экран команды первым делом
 * спрашивает именно этот адрес, чтобы решить, показать карточку или форму
 * основания. Поэтому у ответа одна форма, а отсутствие команды выражено
 * пустыми полями.
 */
@Schema(description = "Своя рейтинговая команда")
public record MyTeamResponseDTO(

        @Schema(description = "Карточка команды; null — команды нет")
        TeamCardView team,

        @Schema(description = "Напарник; null — команды нет")
        TeamPartnerView partner,

        @Schema(description = "Показатели по режимам; null — команды нет")
        TeamStatsView stats,

        @Schema(description = "Свой дивизион: он есть и без команды, форма основания показывает его "
                + "как ограничение — напарника можно звать только из своей лиги")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Готовая подпись дивизиона с флагом. Считает сервер: без команды это "
                + "название языка, с командой — название дивизиона, и правило это живёт в одном месте",
                example = "🇷🇺 Русский дивизион")
        String divisionBadge) {
}
