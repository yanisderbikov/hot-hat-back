package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Systemd-юнит и порт, который он должен слушать, — одной строкой.
 *
 * <p>Раньше состояние юнита и состояние порта лежали в двух разных картах
 * ({@code services} и {@code ports}), а сводил их клиент по совпадению имени
 * ключа. Совпадение это ниоткуда не следовало: {@code turn} в одной карте
 * отвечал ключу {@code turn_tls} в другой.
 */
@Schema(description = "Состояние службы на машине")
public record HostServiceStateView(

        @Schema(description = "Как служба называется в админке", example = "LiveKit")
        String label,

        @Schema(description = "Имя systemd-юнита", example = "livekit")
        String unit,

        @Schema(description = "Что ответил systemctl is-active", example = "active")
        String systemdState,

        @Schema(description = "Юнит запущен", example = "true", type = "boolean")
        boolean running,

        @Schema(description = "Порт, который служба должна слушать; null — порт для неё не объявлен",
                example = "7880", type = "integer", nullable = true)
        Integer port,

        @Schema(description = "Порт слушается; null — порт не объявлен и проверять нечего",
                example = "true", type = "boolean", nullable = true)
        Boolean portListening) {
}
