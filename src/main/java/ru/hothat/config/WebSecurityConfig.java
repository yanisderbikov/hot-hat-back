package ru.hothat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * ALLOWED_ORIGINS раньше проверялся вручную в originIsAllowed(); теперь это
 * обычный CORS Spring.
 *
 * <p>Публичны ровно те маршруты, у которых своя проверка вместо
 * пользовательского токена или которым токен ещё неоткуда взять: вход и
 * восстановление пароля, живость и флаги, справочник дивизионов и проверка
 * ника, вебхук Egress с подписью, уборка и мониторинг со своими секретами,
 * рукопожатие сокета.
 *
 * <p>Матчеров старой поверхности здесь больше нет — ни одного. Вместе с
 * документным шлюзом и контроллерами {@code /api/portal}, {@code /api/game},
 * {@code /api/recordings}, {@code /api/admin}, {@code /api/media},
 * {@code /api/token}, {@code /api/tts}, {@code /api/test-bots},
 * {@code /api/analytics}, {@code /api/features}, {@code /api/health},
 * {@code /api/geo} и {@code /api/nickname-available} ушли и правила к ним:
 * матчер к несуществующему адресу не охраняет ничего, но читается как
 * обещание, что адрес есть.
 */
@Configuration
@EnableWebSecurity
// @PreAuthorize на use-case: право живёт рядом со сценарием, а не
// россыпью ручных if внутри сервисов (сегодня их четыре стиля).
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ApiAuthErrorHandler apiAuthErrorHandler;

    @Value("${allowed.origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Маршрутов /api/auth здесь больше нет: старый контроллер
                        // входа удалён вместе со своим движком. Вход, регистрация,
                        // гость, обмен и восстановление живут только в /api/v2/auth
                        // и пишут в таблицы схемы v2 — второго входа, который писал
                        // бы в app_user, не осталось.
                        //
                        // Вход и восстановление пароля — до появления токена.
                        // Выход отовсюду и смена пароля требуют его и сюда не входят.
                        // Без этих строк вход в новую поверхность невозможен:
                        // цепочка отвечает 401 раньше,
                        // чем дело дойдёт до метода, а на сценариях уже написано
                        // permitAll() — и это право осталось бы враньём. Пути точные,
                        // без "/**": по соседству лежат адреса, которым токен нужен
                        // (/accounts/upgrades меняет гостя на игрока, /me/** — свои
                        // настройки), и они должны остаться под authenticated().
                        .requestMatchers(HttpMethod.POST, "/api/v2/auth/accounts",
                                "/api/v2/auth/sessions", "/api/v2/auth/sessions/guest",
                                "/api/v2/auth/sessions/renewal", "/api/v2/auth/password-resets",
                                "/api/v2/auth/password-resets/completions").permitAll()
                        // Выход отдаёт refresh-токен, а не access: у истёкшей сессии
                        // access-токена уже нет, и требовать его на выходе значило бы
                        // не дать выйти именно тому, кому это нужно.
                        .requestMatchers(HttpMethod.DELETE, "/api/v2/auth/sessions/current").permitAll()
                        // Проверка ника — наследник /api/nickname-available: её
                        // спрашивает форма регистрации, где токена ещё нет.
                        .requestMatchers(HttpMethod.GET, "/api/v2/auth/accounts/nickname-availability").permitAll()
                        // Живость спрашивает балансировщик, флаги — экран
                        // регистрации, где токена ещё нет. Секретов ни в том, ни
                        // в другом нет, а перечислить флаги нельзя: их спрашивают
                        // по имени.
                        .requestMatchers(HttpMethod.GET, "/api/v2/app/health", "/api/v2/app/features/**").permitAll()
                        // Вебхук LiveKit Egress: аккаунта у вызывающего нет,
                        // подлинность он доказывает подписью на секрете LiveKit.
                        // Старый адрес оставлен до отдельной выкатки, новый —
                        // /api/v2/machine/webhooks/livekit-egress.
                        .requestMatchers("/api/recording-egress").permitAll()
                        // Файл мема подставляют в <video src> и <img src>, а туда
                        // браузер заголовок Authorization не передаёт: под общим
                        // authenticated() библиотека мемов просто не проигралась бы.
                        .requestMatchers(HttpMethod.GET, "/api/v2/media/files/**").permitAll()
                        // Справочник дивизионов нужен экрану регистрации, где токена
                        // ещё нет. Оба сценария объявлены permitAll и в @PreAuthorize —
                        // без записи здесь до метода дело бы не дошло и права
                        // остались бы враньём.
                        .requestMatchers(HttpMethod.GET, "/api/v2/profile/divisions",
                                "/api/v2/profile/divisions/suggestion").permitAll()
                        .requestMatchers("/api/cleanup-rooms", "/api/cleanup-recordings").permitAll()
                        .requestMatchers("/api/monitor").permitAll()
                        // Рукопожатие сокета проверяет токен само, в AuthHandshakeInterceptor:
                        // заголовок Authorization браузер туда передать не может.
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/actuator/**").permitAll()
                        // Владелец сюда входит наравне с администратором, потому что
                        // две роли считаются по разным спискам (A5): владельца
                        // задаёт hot-hat.owner-email, администратора — admin-emails
                        // и admin-uids, и владелец может не значиться ни в одном из
                        // них. Без этой строки его собственный реестр учёток
                        // /api/v2/admin/users отвечал бы ему 403 до того, как дело
                        // дойдёт до hasRole('OWNER') на сценарии. Матчер здесь —
                        // второй рубеж: точное право объявлено на каждом use-case.
                        .requestMatchers("/api/v2/admin/**").hasAnyRole("ADMIN", "OWNER")
                        // Новая поверхность решает права на сценарии: у каждого
                        // use-case свой @PreAuthorize, и ровно десять из них
                        // называют GUEST (§8 плана). Здесь поэтому только
                        // authenticated() — иначе матчер отверг бы гостя раньше,
                        // чем дело дойдёт до правила, и слово GUEST на сценарии
                        // осталось бы враньём.
                        .requestMatchers("/api/v2/**").authenticated()
                        // Правило по умолчанию — hasRole('USER'), а не
                        // authenticated(). Старой поверхности, ради которой оно
                        // писалось, больше нет, но смысл правила от этого не
                        // изменился: сюда попадает всё неназванное, и гость
                        // не должен проходить в него молча, только потому что
                        // у него появилась личность. Новый адрес, забытый в
                        // списке выше, закрывается этим правилом сам.
                        .anyRequest().hasRole("USER"))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(apiAuthErrorHandler)
                        .accessDeniedHandler(apiAuthErrorHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
