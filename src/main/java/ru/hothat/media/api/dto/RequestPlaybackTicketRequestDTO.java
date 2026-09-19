package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ru.hothat.media.domain.MemeAssetKey;

/**
 * Заявка на билет для воспроизведения файла мема.
 *
 * <p>Прежде этот запрос ходил по тому же адресу, что и билеты на загрузку,
 * и отличался от них полем {@code kind}. Общего у них только слово «билет»:
 * загрузка кладёт файл, который ещё не существует, и живёт десять минут;
 * воспроизведение открывает существующий на два часа, чтобы браузер
 * переиспользовал кеш и range-запросы, и ничего не создаёт — поэтому здесь
 * 200, а не 201.
 */
@Schema(description = "Запрос подписанной ссылки на воспроизведение")
public record RequestPlaybackTicketRequestDTO(

        @Schema(description = "Ключ файла в хранилище: он приходит в карточке мема",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/video.webm",
                pattern = MemeAssetKey.PATTERN)
        @NotBlank(message = "Нужен путь файла в хранилище.")
        @Pattern(regexp = MemeAssetKey.PATTERN, message = "Некорректный путь файла в хранилище.")
        String storagePath) {
}
