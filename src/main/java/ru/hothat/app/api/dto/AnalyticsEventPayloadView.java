package ru.hothat.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Подробности события: ровно шесть полей и ни одним больше.
 *
 * <p>Границы взяты из {@code AnalyticsServiceImpl.sanitizePayload}, но
 * применяются иначе. Там значение вне границ молча прижималось к краю:
 * клиент с ошибкой в счётчике получал {@code ok} и не узнавал, что в отчёт
 * поехала неправда. Здесь границы объявлены проверкой — запрос с
 * {@code playerCount: 99} отвергается и виден в консоли разработчика.
 *
 * <p>Все поля необязательные и обёрнуты объектными типами: «не задано» и
 * «задано нулём» — разные случаи. Событие входа в учётку не знает ни номера
 * партии, ни числа слов, и подставлять им нули значило бы записать в отчёт
 * партию нулевой длины.
 */
@Schema(description = "Подробности клиентского события")
public record AnalyticsEventPayloadView(

        @Schema(description = "Название комнаты, как его видел игрок", example = "Комната Васи",
                maxLength = 80, nullable = true)
        @Size(max = 80, message = "Название комнаты длиннее 80 символов.")
        String roomName,

        @Schema(description = "Сколько игроков было в комнате в момент события", example = "6",
                type = "integer", nullable = true)
        @Min(value = 0, message = "Число игроков не может быть отрицательным.")
        @Max(value = 10, message = "В комнате не бывает больше десяти игроков.")
        Integer playerCount,

        @Schema(description = "Сколько команд было в комнате", example = "3", type = "integer", nullable = true)
        @Min(value = 0, message = "Число команд не может быть отрицательным.")
        @Max(value = 5, message = "В комнате не бывает больше пяти команд.")
        Integer teamCount,

        @Schema(description = "Сколько слов было в шляпе", example = "120", type = "integer", nullable = true)
        @Min(value = 0, message = "Число слов не может быть отрицательным.")
        @Max(value = 5000, message = "Больше пяти тысяч слов в шляпе не бывает.")
        Integer wordCount,

        @Schema(description = "Номер партии внутри комнаты", example = "2", type = "integer", nullable = true)
        @Min(value = 0, message = "Номер партии не может быть отрицательным.")
        @Max(value = 10000, message = "Номер партии слишком велик.")
        Integer gameNumber,

        @Schema(description = "Сколько секунд длилась партия", example = "930", type = "integer", nullable = true)
        @Min(value = 0, message = "Длительность не может быть отрицательной.")
        @Max(value = 86400, message = "Партия не может идти дольше суток.")
        Integer durationSeconds) {
}
