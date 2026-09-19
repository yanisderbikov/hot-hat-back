package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кадр обновления канала {@code /ws/v2/media/memes}.
 *
 * <p>Заменяет живую подписку на коллекцию {@code memeLibrary}
 * ({@code app-core.js:6037}). Уходит после фиксации транзакции: до неё в чужой
 * сетке появился бы мем, которого при откате не станет, — а его ещё и заряжают
 * в обойму.
 */
@Schema(description = "Кадр изменения библиотеки мемов")
public record MemeLibraryEventDTO(

        @Schema(description = "Имя кадра", example = "memes", allowableValues = "memes")
        String type,

        @Schema(description = "Библиотека после изменения")
        MemeLibraryWindowView library) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public MemeLibraryEventDTO(MemeLibraryWindowView library) {
        this("memes", library);
    }
}
