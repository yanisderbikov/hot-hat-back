package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Боевое снаряжение игрока в партии.
 *
 * <p>Обойма мемов разложена по трём спискам, а не одним: доступные стреляют
 * сейчас, резерв ждёт награды за три угаданных слова, переработка вернётся,
 * когда резерв кончится. Панель арсенала рисует их по-разному, и свести их в
 * один список значило бы заставить клиент вычислять эту разницу самому.
 */
@Schema(description = "Боезапас и обойма мемов")
public record ArsenalView(

        @Schema(description = "Заряды по видам оружия",
                example = "{\"meme\":4,\"tomato\":7,\"crocodile\":2,\"voice\":4,\"negative\":1,\"apozh\":1,"
                        + "\"replacement\":1,\"object\":1,\"poop\":1,\"megaText\":1}")
        Map<String, Integer> ammo,

        @Schema(description = "Пять заряженных мемов", example = "[\"meme-1a2b3c4d5e6f\",\"meme-2b3c4d5e6f7a\"]")
        List<String> loadout,

        @Schema(description = "Мемы, готовые к выстрелу", example = "[\"meme-1a2b3c4d5e6f\"]")
        List<String> available,

        @Schema(description = "Мемы, ждущие следующей награды", example = "[\"meme-2b3c4d5e6f7a\"]")
        List<String> reserve,

        @Schema(description = "Отстрелянные мемы: вернутся, когда кончится резерв", example = "[]")
        List<String> recycle,

        @Schema(description = "До какого момента идёт перезарядка, миллисекунды эпохи", example = "1757150383000")
        long cooldownUntilMs) {
}
