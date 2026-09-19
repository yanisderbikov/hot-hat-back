package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Игровая комната, заведённая «этим составом» из видео-чата.
 *
 * <p>По ней участники переходят в игру: увидев в кадре свою комнату, экран
 * созвона отключает видеосвязь и открывает комнату по ссылке.
 */
@Schema(description = "Комната, заведённая из видео-чата")
public record ConferenceGameRoomView(

        @Schema(description = "Идентификатор комнаты", example = "hat-0f3a9c1d7b2e5480")
        String roomId,

        @Schema(description = "Кому в ней положено место: те, кто был в звонке при создании",
                example = "[\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\"]")
        List<String> memberUids,

        @Schema(description = "Когда заведена, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs) {
}
