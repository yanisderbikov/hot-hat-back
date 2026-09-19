package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.RoomId;

/**
 * Комната, в которой игрок сейчас находится.
 *
 * <p>Одна метка на учётную запись, а не список: она нужна ровно для того,
 * чтобы вернуть человека в идущую партию после перезагрузки вкладки
 * ({@code resumeActiveGameForUser}). Играть в двух комнатах одновременно
 * всё равно нельзя, и вторая метка означала бы, что куда-то возвращать
 * можно в двух местах сразу.
 */
@Schema(description = "Комната, в которой игрок сейчас играет")
public record SetActiveRoomRequestDTO(

        @Schema(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480",
                pattern = "^hat-[a-f0-9]{16}$")
        @NotBlank(message = "Не указана игровая комната.")
        @RoomId
        String roomId) {
}
