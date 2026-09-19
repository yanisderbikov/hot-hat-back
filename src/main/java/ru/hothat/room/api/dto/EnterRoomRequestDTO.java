package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Заявка на место в комнате.
 *
 * <p>Ни имени, ни аватара, ни обоймы мемов здесь нет: всё это лежит в карточке
 * игрока, и сервер берёт их оттуда. До сих пор их присылал браузер вместе с
 * местом ({@code joinRoom()}, {@code app-core.js:12277}), то есть под своим
 * именем в комнату мог сесть кто угодно.
 *
 * <p>Единственное поле — намерение, которого сервер знать не может: пришёл ли
 * игрок из подбора. Оно и переносит сюда {@code autoAssignMatchmadeTeam()}
 * ({@code :12232}), где браузер сам выбирал себе команду посвободнее.
 */
@Schema(description = "Заявка на вход в комнату")
public record EnterRoomRequestDTO(

        @Schema(description = "Посадить в команду автоматически: так входят из подбора, где "
                + "выбирать команду руками негде. Свободного места нет — игрок остаётся "
                + "в комнате без команды, и это не ошибка",
                example = "false", type = "boolean", nullable = true)
        Boolean autoAssignTeam) {

    public boolean autoAssignTeamOrDefault() {
        return Boolean.TRUE.equals(autoAssignTeam);
    }
}
