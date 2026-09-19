package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Первый кадр канала {@code /ws/v2/media/memes}.
 *
 * <p>Приезжает сразу после рукопожатия и уже несёт библиотеку: иначе экран
 * арсенала был бы обязан сходить ещё и по HTTP, чтобы показать хоть одну
 * карточку.
 */
@Schema(description = "Кадр открытия канала библиотеки мемов")
public record MemeLibraryHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Библиотека на момент открытия канала")
        MemeLibraryWindowView library) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public MemeLibraryHelloDTO(MemeLibraryWindowView library) {
        this("hello", library);
    }
}
