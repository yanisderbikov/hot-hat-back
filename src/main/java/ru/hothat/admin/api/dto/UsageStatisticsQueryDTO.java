package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Отрезок дней, за который считается статистика.
 *
 * <p>Тип {@link LocalDate}, а не строка: разбор даты — дело конвертера, и
 * нечитаемое значение теперь честно отвечает 400. Прежний контроллер молча
 * подменял его неделей ({@code AdminController:64-74}), и администратор,
 * попросивший июль, получал неделю без единого слова.
 *
 * <p>Границы необязательны: первый заход на экран случается раньше, чем
 * человек что-то выберет. Что происходит с пустым, перевёрнутым и слишком
 * длинным отрезком, решает {@code DateRange} — здесь этого правила нет.
 */
@Schema(description = "Границы периода статистики")
public record UsageStatisticsQueryDTO(

        @Parameter(description = "Первый день периода включительно, UTC; "
                + "не задан — неделя до конца периода", example = "2026-08-30")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate start,

        @Parameter(description = "Последний день периода включительно, UTC; не задан — сейчас",
                example = "2026-09-06")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate end) {
}
