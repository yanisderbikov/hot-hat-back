package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Зарядить обойму мемов на партию.
 *
 * <p>Ровно пять и без повторов: обойма — это пять слотов, и партия не начнётся,
 * пока каждый не зарядил все пять. Проверка повторов и существования роликов —
 * на сервере: раньше её делал клиент, и незаряженный мем всплывал уже
 * выстрелом.
 */
@Schema(description = "Обойма мемов на партию")
public record SetMatchLoadoutRequestDTO(

        @Schema(description = "Пять разных мемов",
                example = "[\"meme-1a2b3c4d5e6f\",\"meme-2b3c4d5e6f7a\",\"meme-3c4d5e6f7a8b\","
                        + "\"meme-4d5e6f7a8b9c\",\"builtin-bmw-drugoy-ne-znayu\"]")
        @NotNull(message = "Нужно прислать обойму.")
        @Size(min = 5, max = 5, message = "В обойме ровно 5 мемов.")
        List<@NotNull String> memeIds) {
}
