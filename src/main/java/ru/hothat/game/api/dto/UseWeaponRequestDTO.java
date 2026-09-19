package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import ru.hothat.game.domain.WeaponType;

/**
 * Применить оружие.
 *
 * <p>Один адрес на все тринадцать видов вместо тринадцати адресов — решение
 * заказчика (вопрос 2 плана). Вид оружия — перечисление, поэтому неизвестное
 * значение отвергает Jackson до входа в контроллер, и {@code switch} по строке
 * не появляется нигде.
 *
 * <p>Три поля нужны не всякому оружию: {@code memeId} — только мему,
 * {@code clipId} — только Подмене, координаты — только накладкам. Проверяет
 * это обработчик своего вида; контроллер не ветвится.
 */
@Schema(description = "Запрос на применение оружия")
public record UseWeaponRequestDTO(

        @Schema(description = "Вид оружия", example = "tomato",
                allowableValues = {"meme", "tomato", "crocodile", "voice_bogdan", "voice_prokurish",
                        "voice_apozh", "negative", "replacement", "mask_kit_penot", "object", "poop",
                        "mega_text", "fart"})
        @NotNull(message = "Не выбрано оружие.")
        WeaponType type,

        @Schema(description = "Какой мем показать; только для оружия meme", example = "meme-7b1c2d3e4f5a",
                nullable = true)
        String memeId,

        @Schema(description = "Какой клип показать; только для Подмены", example = "repl_9f8e7d6c5b4a39281706",
                nullable = true)
        String clipId,

        @Schema(description = "Доля ширины сцены для накладки, 0.02..0.98", example = "0.42", nullable = true)
        @DecimalMin(value = "0.0", message = "Точка вне сцены.")
        @DecimalMax(value = "1.0", message = "Точка вне сцены.")
        Double x,

        @Schema(description = "Доля высоты сцены для накладки, 0.02..0.98", example = "0.61", nullable = true)
        @DecimalMin(value = "0.0", message = "Точка вне сцены.")
        @DecimalMax(value = "1.0", message = "Точка вне сцены.")
        Double y) {
}
