package ru.hothat.machine.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import ru.hothat.config.ApiAuthErrorHandler;
import ru.hothat.machine.domain.MachineRoute;

/**
 * Права машинной поверхности — одной декларацией.
 *
 * <p>Это тот самый {@code securityMatcher} на поддерево, о котором говорит план.
 * Сегодня все пять машинных маршрутов лежат в {@code permitAll}
 * ({@code WebSecurityConfig:58-62}), то есть по декларации открыты всем, а
 * настоящая проверка спрятана в теле метода. Здесь наоборот: цепочка требует
 * удостоверённого запроса, роль выдаёт {@link MachineActorFilter}, а какая
 * именно роль нужна адресу — сказано {@code @PreAuthorize} на сценарии.
 *
 * <p>Своя цепочка, а не строки в общей, по двум причинам. Первая: у машин нет
 * пользовательского токена, и {@code JwtAuthFilter} им не нужен вовсе.
 * Вторая: поддерево описано одним выражением, поэтому новый машинный адрес
 * попадает под ту же защиту сам, без правки списка маршрутов.
 */
@Configuration
public class MachineSecurityConfig {

    /** Схема для тех, кого настраиваем мы: рекордер, cron, агент мониторинга. */
    public static final String SECRET_SCHEME = "MachineSecret";
    /** Схема вебхука: токен кладёт LiveKit, а не мы. */
    public static final String LIVEKIT_SCHEME = "LiveKitWebhook";

    /**
     * Порядок важен: цепочка без {@code securityMatcher} в
     * {@code WebSecurityConfig} совпадает с любым запросом, поэтому машинная
     * должна стоять раньше неё.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain machineSecurityFilterChain(HttpSecurity http,
                                                          MachineActorFilter machineActorFilter,
                                                          ApiAuthErrorHandler apiAuthErrorHandler,
                                                          CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.securityMatcher(MachineRoute.PREFIX + "/**")
                .csrf(csrf -> csrf.disable())
                // Страницу рекордера открывает браузер Egress с адреса сайта,
                // а API живёт на другом origin: без CORS его запросы не уйдут.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(apiAuthErrorHandler)
                        .accessDeniedHandler(apiAuthErrorHandler))
                .addFilterBefore(machineActorFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Фильтр — бин типа {@code Filter}, и Spring Boot иначе повесил бы его ещё и
     * на все запросы подряд, мимо цепочки. Одного вызова внутри цепочки
     * достаточно: за её пределами машинных адресов нет.
     */
    @Bean
    public FilterRegistrationBean<MachineActorFilter> machineActorFilterNotGlobal(
            MachineActorFilter machineActorFilter) {
        FilterRegistrationBean<MachineActorFilter> registration =
                new FilterRegistrationBean<>(machineActorFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Замок в спецификации. Без этого у машинных операций в Swagger UI не было
     * бы вовсе никакого способа передать удостоверение — сегодня у них нет и
     * описания параметров, не то что схемы доступа.
     */
    @Bean
    public OpenApiCustomizer machineSecuritySchemes() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents()
                    .addSecuritySchemes(SECRET_SCHEME, new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER)
                            .name(MachineActorFilter.SECRET_HEADER)
                            .description("Секрет машинного актора: подпись съёмки для рекордера, "
                                    + "CRON_SECRET для уборки, HOT_HAT_MONITOR_SECRET для агента"))
                    .addSecuritySchemes(LIVEKIT_SCHEME, new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Подписанный токен вебхука LiveKit из заголовка Authorization"));
        };
    }
}
