package ru.hothat.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Единый формат ошибки.
 *
 * <p>Первые два поля — с прежнего API: клиент разбирает машинный {@code code},
 * а {@code error} показывает пользователю. Остальные — для отладки: по ним
 * отказ читается прямо из консоли браузера, а по {@code requestId} находится
 * в логе сервера — та же строка стоит в заголовке {@code X-Request-Id} и в
 * каждой записи лога этого запроса.
 *
 * <p>{@code details} есть только у {@code VALIDATION_FAILED}, по нарушению на
 * строку. {@code detail} — только у 5xx и только при включённом
 * {@code hot-hat.errors.expose-details}: в причине исключения может лежать
 * SQL или путь на диске, игроку это видеть незачем.
 *
 * @param error     понятный человеку текст
 * @param code      машинный код: AUTH_REQUIRED, NICKNAME_TAKEN, VALIDATION_FAILED…
 * @param status    HTTP-статус ответа
 * @param method    метод запроса
 * @param path      адрес запроса; токен в строке запроса замаскирован
 * @param timestamp момент ответа, UTC
 * @param requestId идентификатор запроса, общий с логом сервера
 * @param details   нарушения проверки, только у VALIDATION_FAILED
 * @param detail    причина сбоя сервера, только у 5xx при включённом флаге
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ошибка API")
public record ErrorDTO(

        @Schema(description = "Понятный человеку текст", example = "Нет прав администратора.")
        String error,

        @Schema(description = "Машинный код", example = "ADMIN_REQUIRED")
        String code,

        @Schema(description = "HTTP-статус ответа", example = "403")
        int status,

        @Schema(description = "Метод запроса", example = "POST")
        String method,

        @Schema(description = "Адрес запроса; токен в строке запроса замаскирован",
                example = "/api/v2/admin/users")
        String path,

        @Schema(description = "Момент ответа, UTC", example = "2026-09-19T08:34:54.354Z")
        String timestamp,

        @Schema(description = "Идентификатор запроса: тот же в заголовке X-Request-Id и в логе сервера",
                example = "a1b2c3d4")
        String requestId,

        @Schema(description = "Нарушения проверки по одному на строку; только у VALIDATION_FAILED")
        List<String> details,

        @Schema(description = "Причина сбоя сервера; только у 5xx и только при включённом "
                + "hot-hat.errors.expose-details")
        String detail) {
}
