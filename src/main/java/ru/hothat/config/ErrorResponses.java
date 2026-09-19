package ru.hothat.config;

import jakarta.servlet.http.HttpServletRequest;
import ru.hothat.dto.common.ErrorDTO;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Единственное место, где собирается тело ошибки.
 *
 * <p>Тела пишут двое — {@link GlobalExceptionHandler} для исключений из
 * контроллеров и {@link ApiAuthErrorHandler} для отказов охраны, которые до
 * контроллера не доходят. Раньше каждый складывал {@code {error, code}} сам;
 * с полями про запрос такое дублирование разъехалось бы на первой же правке.
 */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    /** Текст — из словаря по коду; {@code LOADOUT_REQUIRED:имя} и прочие хвосты срезаются как раньше. */
    public static ErrorDTO of(HttpServletRequest request, int status, String code) {
        return of(request, status, code, ErrorMessages.resolve(code, status));
    }

    /** Текст задан явно: проверки и промахи вызывающего формулируют его сами. */
    public static ErrorDTO of(HttpServletRequest request, int status, String code, String message) {
        return build(request, status, ErrorMessages.publicCode(code), message, null, null);
    }

    /**
     * Не прошла проверка. В {@code error} нарушения склеены через «; » — так
     * их показывал прежний API, и формы на это рассчитывают; в {@code details}
     * они же по одному, для консоли и для форм с подсветкой полей.
     */
    public static ErrorDTO validation(HttpServletRequest request, List<String> details) {
        String message = details.isEmpty() ? "Некорректный запрос" : String.join("; ", details);
        return build(request, 400, "VALIDATION_FAILED", message, details, null);
    }

    /** Сбой сервера. {@code detail} — причина, если её решено показывать; иначе {@code null}. */
    public static ErrorDTO internal(HttpServletRequest request, String detail) {
        return build(request, 500, "INTERNAL", ErrorMessages.resolve("INTERNAL", 500), null, detail);
    }

    private static ErrorDTO build(HttpServletRequest request, int status, String code, String message,
                                  List<String> details, String detail) {
        return new ErrorDTO(
                message,
                code,
                status,
                request == null ? null : request.getMethod(),
                request == null ? null : RequestLogFilter.pathWithQuery(request),
                // До миллисекунд — как в логе, чтобы сверять глазом.
                Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(),
                RequestLogFilter.requestId(request),
                details,
                detail);
    }
}
