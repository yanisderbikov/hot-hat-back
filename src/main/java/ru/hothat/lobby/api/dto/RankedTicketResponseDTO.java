package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Рейтинговая пара поставлена в подбор.
 *
 * <p>Заменяет {@code portal matchmake} с {@code ranked=true}
 * ({@code portal.js:127}). Пара занимает слот команды целиком: разлучать
 * напарников нельзя, поэтому в комнате всегда освобождается место сразу под
 * двоих.
 *
 * <p>Читается заявка тем же адресом, что и быстрая: у неё тот же
 * идентификатор и то же состояние.
 */
@Schema(description = "Поданная рейтинговая заявка")
public record RankedTicketResponseDTO(

        @Schema(description = "Поданная заявка")
        MatchTicketView ticket) {
}
