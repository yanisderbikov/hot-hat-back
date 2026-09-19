package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Новая редакция своего сообщения.
 *
 * <p>Правится только текст: подменить фотографию нельзя — соседи по столу уже
 * увидели ту, что была, и «правка» превратилась бы в подлог.
 */
@Schema(description = "Новая редакция сообщения")
public record EditRoomChatMessageRequestDTO(

        @Schema(description = "Новый текст сообщения", example = "Скидывайте слова, начинаем через минуту",
                maxLength = 300)
        @NotBlank(message = "Сообщение не может быть пустым.")
        @Size(max = 300, message = "Сообщение не длиннее 300 символов.")
        String text) {
}
