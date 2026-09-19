package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Отбор страницы личной библиотеки записей.
 *
 * <p>Курсора в запросе нет намеренно: движок за этим адресом читает сотню
 * последних сохранённых записей и отдаёт их одной страницей,
 * {@code nextCursor} в ответе всегда пуст. Принимать курсор, которого некому
 * выдать, — обман.
 */
@Schema(description = "Параметры страницы личной библиотеки записей")
public record MyRecordingQueryDTO(

        /**
         * Обёрточный тип, а не int: «не задано» и «задано нулём» — разные
         * случаи, а единственное место дефолта живёт в сценарии.
         */
        @Parameter(description = "Сколько записей вернуть; по умолчанию 50, потолок — сотня, "
                + "с которой работает движок", example = "50")
        @Min(value = 1, message = "Размер страницы должен быть положительным.")
        @Max(value = 100, message = "Больше ста записей за раз не отдаём.")
        Integer limit) {
}
