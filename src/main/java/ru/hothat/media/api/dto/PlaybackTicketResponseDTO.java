package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Билет на воспроизведение: подписанная ссылка на два часа.
 *
 * <p>Проверять существование файла перед выдачей ссылки сервер намеренно не
 * ходит: это удваивало бы платные обращения к хранилищу, а пропавший объект
 * всё равно проявится на первом же запросе браузера.
 */
@Schema(description = "Подписанная ссылка на файл мема")
public record PlaybackTicketResponseDTO(

        @Schema(description = "Ссылка для проигрывания; живёт два часа",
                example = "https://s3.hot-hat.ru/hot-hat/memes/ru/Qk3xZaTb/meme-9f31ab77c204/video.webm?X-Amz-Signature=…")
        String playbackUrl,

        @Schema(description = "Ключ файла, на который выдана ссылка: по нему клиент хранит её в своём кеше",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/video.webm")
        String storagePath,

        @Schema(description = "Когда ссылка перестанет действовать, миллисекунды эпохи",
                example = "1788607200000", type = "integer")
        long expiresAtMs) {
}
