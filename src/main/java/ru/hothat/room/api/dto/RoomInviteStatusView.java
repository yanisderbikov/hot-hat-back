package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состояние одного приглашения в комнату.
 *
 * <p>Общая проекция: включается и в построчный ответ на список, и в ответ на
 * принятие приглашения.
 *
 * <p>Видимость режется здесь, а не в правах: список принимает до шестидесяти
 * идентификаторов сразу, и предикат уровня класса к такому запросу неприменим
 * в принципе. Чужое приглашение отвечает {@code forbidden} и не называет ни
 * комнаты, ни отправителя — по номеру приглашения нельзя узнать, чьё оно.
 */
@Schema(description = "Состояние приглашения в комнату")
public record RoomInviteStatusView(

        @Schema(description = "Идентификатор приглашения", example = "ri-3f9a2b1c7d8e0f4a")
        String inviteId,

        @Schema(description = "Состояние приглашения", example = "pending")
        RoomInviteState state,

        @Schema(description = "Можно ли по нему войти прямо сейчас", example = "true", type = "boolean")
        boolean available,

        @Schema(description = "Комната, куда зовут; null — приглашение чужое или комнаты нет",
                example = "hat-0f3a9c1d7b2e5480", nullable = true)
        String roomId,

        @Schema(description = "Название комнаты; null — приглашение чужое или комнаты нет",
                example = "Комната Васи", nullable = true)
        String roomName,

        @Schema(description = "Заряжены ли у получателя все пять мемов: без них в комнату не пускают "
                + "ни по какому пути. У чужого приглашения null",
                example = "true", type = "boolean", nullable = true)
        Boolean loadoutReady,

        @Schema(description = "Сколько мемов заряжено у получателя. У чужого приглашения null",
                example = "5", type = "integer", nullable = true)
        Integer loadoutCount) {
}
