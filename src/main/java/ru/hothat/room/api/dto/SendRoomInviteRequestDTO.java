package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.PlayerUid;

/**
 * Кого позвать в свою комнату.
 *
 * <p>Только друга: приглашение уезжает сообщением в личную переписку, а
 * переписка и так открыта только друзьям и напарнику по рейтинговой команде.
 * Звать в рейтинговую комнату нельзя вовсе — её состав собирает подбор.
 */
@Schema(description = "Приглашение друга в комнату")
public record SendRoomInviteRequestDTO(

        @Schema(description = "Кого зовём", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4",
                pattern = "^[A-Za-z0-9_-]{1,160}$")
        @NotBlank(message = "Не выбран друг.")
        @PlayerUid
        String friendUid) {
}
