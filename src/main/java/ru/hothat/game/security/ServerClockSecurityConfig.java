package ru.hothat.game.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Серверные часы открыты всем — своей цепочкой на один адрес.
 *
 * <p>Общая цепочка требует токен на всём {@code /api/v2/**}, и объявленное на
 * сценарии {@code permitAll()} осталось бы враньём: до метода дело не дошло бы.
 * Своя цепочка вместо строки в общей — по образцу машинной поверхности:
 * область не должна править чужую конфигурацию, чтобы объявить своё право.
 *
 * <p>Почему часы вообще без токена: их спрашивает страница записи, которую
 * открывает браузер Egress, и главная страница до входа. Секрета в текущем
 * времени нет.
 */
@Configuration
public class ServerClockSecurityConfig {

    /** Публичный адрес серверных часов. */
    public static final String CLOCK_PATH = "/api/v2/game/clock";

    /**
     * Порядок: раньше общей цепочки, которая совпадает с любым запросом, и
     * позже машинной ({@code @Order(1)}) — их пути не пересекаются.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain serverClockSecurityFilterChain(HttpSecurity http,
                                                              CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.securityMatcher(CLOCK_PATH)
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
