package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Обойма мемов учётной записи — состав и готовность.
 *
 * <p>Сегодня это три ключа внутри {@code ensure_profile}:
 * {@code defaultMemeLoadout}, {@code loadoutCount}, {@code loadoutReady}.
 * Счётчик и признак готовности здесь не повторяются полями — их называет
 * общая проекция {@link MemeLoadoutStatusView}, ту же самую отдаёт карточка
 * профиля. Иначе «сколько заряжено» существовало бы в двух ответах двумя
 * независимыми числами.
 *
 * <p>Порядок мемов значим: это порядок слотов обоймы, и партия раздаёт
 * заряды по нему. Поэтому список, а не множество.
 */
@Schema(description = "Обойма мемов учётной записи")
public record DefaultLoadoutResponseDTO(

        @Schema(description = "Заряженные мемы по порядку слотов, не больше пяти",
                example = "[\"meme-1a2b3c4d5e6f\",\"builtin-bmw-drugoy-ne-znayu\"]")
        List<String> memeIds,

        @Schema(description = "Насколько обойма собрана")
        MemeLoadoutStatusView status) {
}
