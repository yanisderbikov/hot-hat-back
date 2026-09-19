package ru.hothat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Одна строка в логе на каждый HTTP-запрос: метод, адрес, статус, время.
 *
 * <p>Стоит первым среди сервлетных фильтров, то есть снаружи цепочек Spring
 * Security: так в лог попадают и запросы, отвергнутые ещё до контроллера
 * (401 без токена, 403 гостю, preflight OPTIONS), и рукопожатие сокета
 * (101 на {@code /ws/**}). Личности запроса здесь намеренно нет — к моменту
 * возврата из цепочки контекст безопасности уже очищен.
 *
 * <p>Замолчать целиком: {@code logging.level.ru.hothat.config.RequestLogFilter=off}.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
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
            log.info("{} {} {} {} мс", request.getMethod(), pathWithQuery(request), status, tookMs);
        }
    }

    private static String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }
}
