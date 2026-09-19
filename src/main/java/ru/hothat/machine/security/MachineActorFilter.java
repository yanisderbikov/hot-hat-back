package ru.hothat.machine.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hothat.machine.domain.MachineActor;
import ru.hothat.machine.domain.MachineRoute;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Единственная дверь машинных акторов.
 *
 * <p>Приём один на все двенадцать адресов: адрес называет актора
 * ({@link MachineRoute}), удостоверение приезжает заголовком, роль выдаётся
 * здесь, а право дальше выражается обычным {@code @PreAuthorize} на сценарии.
 * Ручных {@code if} внутри методов не остаётся ни одного.
 *
 * <p>Фильтр никогда не пишет ответ сам. Не сошлось удостоверение — он просто
 * не кладёт личность в контекст, и запрос отклоняет {@code authenticated()} в
 * {@link MachineSecurityConfig} через общий {@code ApiAuthErrorHandler}. Так у
 * машинных адресов та же форма ошибки, что у всех остальных, и фильтр не
 * подсказывает, чем именно не подошло удостоверение.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MachineActorFilter extends OncePerRequestFilter {

    /**
     * Один заголовок на всех, у кого мы сами настраиваем вызывающего: рекордер,
     * cron, агент мониторинга. Раньше их было три разных — {@code Authorization:
     * Bearer <CRON_SECRET>}, {@code X-Hot-Hat-Monitor-Secret} и подпись в
     * строке запроса, — и каждый проверялся своим кодом.
     */
    public static final String SECRET_HEADER = "X-Hot-Hat-Machine-Secret";

    private final MachineCredentialVerifier verifier;
    private final LiveKitWebhookVerifier livekitVerifier;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        MachineRoute route = MachineRoute.of(path(request));
        if (route == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (route.actor() == MachineActor.EGRESS) {
            // Подпись LiveKit накрывает тело, поэтому тело читается здесь и
            // едет дальше обёрткой: второй раз поток запроса не открыть.
            CachedBodyRequest cached = new CachedBodyRequest(request, request.getInputStream().readAllBytes());
            if (livekitVerifier.verify(request.getHeader("Authorization"), cached.body(), Instant.now())) {
                authenticate(route);
            }
            filterChain.doFilter(cached, response);
            return;
        }
        if (verifier.verify(route, request.getHeader(SECRET_HEADER))) {
            authenticate(route);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(MachineRoute route) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MachineActorPrincipal(route.actor(), route.scope()), null,
                List.of(new SimpleGrantedAuthority(route.actor().authority()))));
    }

    /** Путь без контекста приложения: сравнивать адреса надо с тем же началом, что в маппингах. */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
    }
}
