package ru.hothat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ответы про доступ должны выглядеть так же, как из sendError() в Vercel-версии:
 * 401 AUTH_REQUIRED без токена и 403 при нехватке прав. Стандартные страницы
 * Spring Security фронтенд разобрать не умеет.
 *
 * <p>Отказов по правам два, и они разным людям: {@code ADMIN_REQUIRED} игроку,
 * {@code REGISTRATION_REQUIRED} гостю. Выбор кода общий с
 * {@link GlobalExceptionHandler#deniedCode} — там же объяснено, почему «нет
 * прав администратора» для гостя тупик.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Тот же резолвер, которым ExceptionTranslationFilter отличает аноним. */
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, 401, "AUTH_REQUIRED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Личность берётся из контекста, а не из наличия заголовка: с просроченным
        // токеном заголовок есть, а личности нет, и прежняя проверка отвечала на
        // это «нет прав администратора» — клиент видел отказ по правам там, где
        // надо было просто обновить пару токенов.
        if (authentication == null || trustResolver.isAnonymous(authentication)) {
            write(request, response, 401, "AUTH_REQUIRED");
            return;
        }
        HotHatUser user = authentication.getPrincipal() instanceof HotHatUser principal ? principal : null;
        write(request, response, 403, GlobalExceptionHandler.deniedCode(user));
    }

    /** Тело — то же, что у {@link GlobalExceptionHandler}: одна форма на оба пути отказа. */
    private void write(HttpServletRequest request, HttpServletResponse response, int status, String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponses.of(request, status, code));
    }
}
