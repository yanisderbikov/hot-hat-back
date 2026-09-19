package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Отбор страницы истории снимков.
 *
 * <p>Границы задаются вдвоём или никак: хранилище умеет либо диапазон, либо
 * последние {@code limit} дней ({@code OpsManager.getUsageDays:48-54}), и
 * достроить недостающий конец самому значило бы ответить не на тот вопрос.
 *
 * <p>Отдельного «выбранного дня» больше нет. Раньше клиент просил один день,
 * посылая одинаковые {@code start} и {@code end}, а сервер отвечал ему тремя
 * дополнительными ключами ({@code selected}, {@code selectedDate},
 * {@code selectedNoData}) — второй формой ответа внутри той же операции (C6).
 * Теперь один день — это диапазон из одного дня и страница из одного элемента.
 */
@Schema(description = "Параметры страницы истории снимков")
public record UsageSnapshotQueryDTO(

        @Parameter(description = "Первый день истории включительно, UTC; работает только вместе с to",
                example = "2026-08-30")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Parameter(description = "Последний день истории включительно, UTC; работает только вместе с from",
                example = "2026-09-06")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        @Parameter(description = "Сколько дней вернуть; по умолчанию 120", example = "120")
        @Min(value = 1, message = "Размер страницы должен быть положительным.")
        @Max(value = 366, message = "Больше года истории за раз не отдаём.")
        Integer limit) {

    /** Тот же предел, с которым историю читал прежний движок мониторинга. */
    public static final int DEFAULT_LIMIT = 120;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
