package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Насколько собрана обойма мемов — счётчиком, без самих мемов.
 *
 * <p>Общая проекция: включается полем в ответ о собственном профиле. Отдельная
 * запись, а не три поля рядом с ником, потому что обойма — не часть карточки
 * игрока: карточку показывают и после первого входа
 * ({@link CompletedOnboardingResponseDTO}), где обоймы ещё нет вовсе.
 *
 * <p>Здесь только счётчик. Сами пять идентификаторов нужны экрану арсенала и
 * приезжают своим адресом ({@code GET /api/v2/profile/me/meme-loadout}):
 * главной странице они не нужны, а весят заметно больше числа.
 *
 * <p>Готовность считает сервер. Раньше правило «ровно пять» жило копией в
 * браузере ({@code home/home.js}: {@code count===5}), и любое изменение
 * размера обоймы означало бы правку в двух местах.
 */
@Schema(description = "Готовность обоймы мемов")
public record MemeLoadoutStatusView(

        @Schema(description = "Сколько мемов заряжено; пустые и повторяющиеся не считаются",
                example = "3", type = "integer", minimum = "0")
        int selected,

        @Schema(description = "Сколько мемов нужно для входа в игру", example = "5", type = "integer")
        int required,

        @Schema(description = "Готова ли обойма: с неготовой в игру не пускают",
                example = "false", type = "boolean")
        boolean ready) {
}
