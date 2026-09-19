package ru.hothat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Отдельная конфигурация ради одного бина — и это не церемония.
 *
 * <p>Пока {@link PasswordEncoder} объявлялся в {@code WebSecurityConfig},
 * получалось кольцо: цепочка фильтров зависит от разбора токена, разбор — от
 * области личности, а область личности — от шифровальщика паролей, то есть
 * снова от конфигурации безопасности. Spring такое кольцо не собирает и
 * отказывается стартовать; проверено вживую.
 *
 * <p>Кольца нет по существу: шифровальщик паролей ничего не знает ни о
 * маршрутах, ни о фильтрах. Он просто лежал не в том файле.
 */
@Configuration
public class PasswordEncoderConfig {

    /** BCrypt: стоимость по умолчанию (10) — компромисс скорости входа и стойкости. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
