package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.hothat.team.api.dto.GameMode;

/**
 * Заявка рейтинговой пары на подбор.
 *
 * <p>Ни размера комнаты, ни языка здесь нет, и это не упущение. Рейтинговая
 * комната всегда на десятерых, а язык рейтинговой партии — всегда родной
 * дивизион команды: движок и сегодня не смотрит на то, что клиент пришлёт в
 * этих полях ({@code MatchmakingServiceImpl:73-77}). Поле, которое ни на что
 * не влияет, лучше не объявлять вовсе, чем объяснять в описании, что оно
 * ничего не делает.
 */
@Schema(description = "Запрос на подбор рейтинговой партии")
public record OpenRankedTicketRequestDTO(

        @Schema(description = "Режим партии, под который идёт подбор", example = "classic")
        @NotNull(message = "Нужен режим партии.")
        GameMode gameMode) {
}
