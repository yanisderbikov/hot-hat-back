package ru.hothat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hothat.auth.spi.AccessTokenPort;

import java.io.IOException;

/**
 * Раньше здесь проверялся Firebase ID-токен, и на каждый запрос уходил сетевой
 * вызов в Google (checkRevoked). Теперь токен свой: подпись проверяется на
 * месте, а в базу идёт одно чтение по первичному ключу — за баном и номером
 * поколения токенов. Это строго дешевле прежней схемы и сохраняет главное
 * её свойство: бан гасит доступ немедленно, а не по истечении TTL.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final AccessTokenPort accessTokens;
    private final PrincipalResolver principalResolver;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token) && accessTokens.isValid(token)) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    /** Список ролей строит {@link PrincipalResolver}: тот же, что у рукопожатия сокета. */
    private void authenticate(String token) {
        principalResolver.resolve(token).ifPresent(principal ->
                SecurityContextHolder.getContext().setAuthentication(principalResolver.authentication(principal)));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }
}
