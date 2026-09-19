package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.hothat.media.api.validation.MemeId;

import java.util.List;

/**
 * Задать обойму мемов учётной записи целиком.
 *
 * <p>Меньше пяти прислать можно, и это не поблажка, а поведение экрана
 * «Мем-арсенал»: он сохраняет выбор на каждом щелчке по ролику
 * ({@code app-core.js:5948-5968} зовёт {@code saveMemeLoadout} и при
 * добавлении, и при снятии). Потребуй адрес ровно пяти — обойму нельзя было
 * бы собрать вовсе: первый же щелчок ушёл бы с одним мемом и получил отказ.
 * Пустой список — это «снять всё».
 *
 * <p>Правило «ровно пять» живёт там, где неполная обойма вредит:
 * {@code requireDefaultLoadout} не пускает с ней в комнату и отвечает
 * 409 {@code DEFAULT_LOADOUT_REQUIRED}. Готовность видно в ответе, так что
 * узнать о ней можно сразу после сохранения, а не отказом на входе в игру.
 *
 * <p>Форма идентификатора проверяется тем же {@link MemeId}, что и у партии:
 * раньше здесь стояли только {@code @NotBlank} и предел длины, и в обойму
 * ложилась любая строка до 120 знаков. Существование ролика формой не
 * проверить — это делает сценарий.
 */
@Schema(description = "Обойма мемов учётной записи")
public record SaveDefaultLoadoutRequestDTO(

        @Schema(description = "Мемы по порядку слотов: до пяти. Меньше пяти — черновик обоймы, "
                + "с ним в игру не пустят; пустой список снимает всё",
                example = "[\"meme-1a2b3c4d5e6f\",\"meme-2b3c4d5e6f7a\",\"meme-3c4d5e6f7a8b\","
                        + "\"meme-4d5e6f7a8b9c\",\"builtin-bmw-drugoy-ne-znayu\"]")
        @NotNull(message = "Нужно прислать обойму.")
        @Size(max = 5, message = "В обойме не больше 5 мемов.")
        List<@NotBlank(message = "Некорректный мем.")
                @MemeId String> memeIds) {
}
