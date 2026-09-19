package ru.hothat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Одна строка в логе на каждый HTTP-запрос: метод, адрес, статус, время —
 * и идентификатор запроса, по которому эта строка, стектрейс и тело ошибки
 * у клиента находят друг друга.
 *
 * <p>Стоит первым среди сервлетных фильтров, то есть снаружи цепочек Spring
 * Security: так в лог попадают и запросы, отвергнутые ещё до контроллера
 * (401 без токена, 403 гостю, preflight OPTIONS), и рукопожатие сокета
 * (101 на {@code /ws/**}). Личности запроса здесь намеренно нет — к моменту
 * возврата из цепочки контекст безопасности уже очищен.
 *
 * <p>Идентификатор рождается здесь же, раньше всех: кладётся в атрибут
 * запроса для {@link ErrorResponses}, в заголовок {@code X-Request-Id}
 * ответа и в MDC — {@code logging.pattern.level} печатает его в каждой
 * строке лога этого потока, пока запрос не закончится.
 *
 * <p>Замолчать целиком: {@code logging.level.ru.hothat.config.RequestLogFilter=off}.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String REQUEST_ID_ATTRIBUTE = RequestLogFilter.class.getName() + ".requestId";
    static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = newRequestId();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        // Заголовок — до цепочки: после commit ответа его уже не добавить.
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_KEY, requestId);

        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable e) {
            failed = true;
            throw e;
        } finally {
            long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
            // Исключение, долетевшее досюда, обработчик контроллеров не поймал:
            // Tomcat ответит 500, а response.getStatus() ещё показывает 200.
            int status = failed ? 500 : response.getStatus();
            if (status >= 500) {
                log.warn("{} {} {} {} мс", request.getMethod(), pathWithQuery(request), status, tookMs);
            } else {
                log.info("{} {} {} {} мс", request.getMethod(), pathWithQuery(request), status, tookMs);
            }
            // Потоки Tomcat переиспользуются: не убрать — чужой запрос
            // унаследует наш идентификатор.
            MDC.remove(MDC_KEY);
        }
    }

    /** Идентификатор запроса или {@code null}, если фильтр запрос не видел (тесты, async). */
    public static String requestId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value == null ? null : value.toString();
    }

    /**
     * Адрес со строкой запроса, но без токена в ней: рукопожатие каналов
     * {@code /ws/v2} несёт токен доступа параметром, и без маски он ложился
     * в лог целиком.
     */
    public static String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + query.replaceAll("(?i)(token=)[^&]*", "$1…");
    }

    /** Восемь шестнадцатеричных знаков: хватает, чтобы найти запрос в логе, и помещается в глаз. */
    private static String newRequestId() {
        return String.format("%08x", ThreadLocalRandom.current().nextInt());
    }
}
