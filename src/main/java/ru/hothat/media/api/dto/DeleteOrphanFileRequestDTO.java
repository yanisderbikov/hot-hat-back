package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ru.hothat.media.domain.MemeAssetKey;

/**
 * Уборка собственного файла, оставшегося без карточки.
 *
 * <p>Так бывает, когда ролик уже уехал в хранилище, а публикация не удалась:
 * клиент прибирает за собой сам ({@code app-core.js:6392}), иначе в бакете
 * копятся оплаченные байты, на которые никто не ссылается.
 *
 * <p>Тело у DELETE, а не путь: ключ содержит косые черты и точки, и в адресе
 * его пришлось бы кодировать. Владелец определяется по самому ключу — свой
 * идентификатор сервер положил туда, когда выдавал билет на загрузку.
 */
@Schema(description = "Запрос на удаление своего осиротевшего файла")
public record DeleteOrphanFileRequestDTO(

        @Schema(description = "Ключ файла в хранилище: тот, что вернул билет на загрузку",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/video.webm",
                pattern = MemeAssetKey.PATTERN)
        @NotBlank(message = "Нужен путь файла в хранилище.")
        @Pattern(regexp = MemeAssetKey.PATTERN, message = "Некорректный путь файла в хранилище.")
        String storagePath) {
}
