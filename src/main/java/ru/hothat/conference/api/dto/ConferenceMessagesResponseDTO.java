package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Окно чата: последние сообщения, старые сверху. */
@Schema(description = "Лента чата видео-чата")
public record ConferenceMessagesResponseDTO(

        @Schema(description = "Сообщения, старые сверху")
        List<ConferenceMessageView> items,

        @Schema(description = "Размер окна: больше этого числа лента не показывает", example = "120",
                type = "integer")
        int limit) {
}
