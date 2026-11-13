package ru.hothat.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * GPT-Live has an explicit short-lived per-session worker secret instead of
 * an end-user JWT. The existing MachineSecurityConfig @Order(1) secures
 * /api/v2/machine/** and would reject this new route before its controller
 * could validate X-HH-AI-WORKER-KEY. This preceding, NARROW chain leaves
 * verification to AiVoiceWorkerController's constant-time 32-byte guard.
 *
 * A missing/invalid worker key is rejected by EVERY controller action.
 */
@Configuration
public class AiVoiceSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain aiVoiceMachineChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v2/machine/gpt-live/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth->auth.anyRequest().permitAll());
        return http.build();
    }
}
