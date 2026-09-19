package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Каталог арсенала.
 *
 * <p>Существует затем, чтобы копия этой таблицы во фронте перестала быть
 * нужна. Стартовый боезапас здесь не отдельная запись, а выжимка из каталога:
 * их расхождение когда-то роняло начисление наград с ошибкой на ключе,
 * которого нет в базовом наборе.
 */
@Schema(description = "Каталог диверсионного оружия")
public record WeaponCatalogResponseDTO(

        @Schema(description = "Все виды оружия")
        List<WeaponView> weapons,

        @Schema(description = "Стартовый боезапас партии",
                example = "{\"meme\":4,\"tomato\":7,\"crocodile\":2,\"voice\":4,\"negative\":1,\"apozh\":1,"
                        + "\"replacement\":1,\"object\":1,\"poop\":1,\"megaText\":1}")
        Map<String, Integer> baseArsenal,

        @Schema(description = "Ключи боезапаса в порядке показа",
                example = "[\"meme\",\"tomato\",\"crocodile\",\"voice\",\"negative\",\"apozh\","
                        + "\"replacement\",\"object\",\"poop\",\"megaText\"]")
        List<String> ammoKeys,

        @Schema(description = "Общая перезарядка между диверсиями, миллисекунды", example = "3000")
        long cooldownMs,

        @Schema(description = "Сколько мемов заряжается в обойму", example = "5")
        int loadoutSize) {
}
