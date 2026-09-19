package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Отбор страницы списка друзей.
 *
 * <p>Курсора в запросе нет намеренно: движок за этим адресом пока отдаёт
 * первую и единственную страницу, {@code nextCursor} в ответе всегда пуст,
 * и принимать значение, которое некому выдать, — обман.
 */
@Schema(description = "Параметры страницы списка друзей")
public record MyFriendsQueryDTO(

        /**
         * Обёрточный тип, а не int: «не задано» и «задано нулём» — разные
         * случаи, а единственное место дефолта живёт в use-case.
         */
        @Parameter(description = "Сколько друзей вернуть; по умолчанию 100 — предел, с которым работает движок",
                example = "100")
        @Min(value = 1, message = "Размер страницы должен быть положительным.")
        @Max(value = 100, message = "Больше ста друзей за раз не отдаём.")
        Integer limit) {
}
