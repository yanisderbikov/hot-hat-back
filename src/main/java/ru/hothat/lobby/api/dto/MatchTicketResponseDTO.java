package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Заявка на быструю игру подана.
 *
 * <p>Заменяет первый вызов {@code portal matchmake} с {@code ranked=false}
 * ({@code home/home.js:99}). Именно первый: раньше опрос шёл тем же вызовом
 * каждые три секунды, и каждый опрос заново перебирал комнаты фазы
 * {@code setup} — из-за чего очередной опрос мог увести игрока в другую
 * комнату. Теперь заявка подаётся один раз и получает адрес, а дальше
 * читается.
 */
@Schema(description = "Поданная заявка на подбор")
public record MatchTicketResponseDTO(

        @Schema(description = "Поданная заявка")
        MatchTicketView ticket) {
}
