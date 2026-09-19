package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отказ канала.
 *
 * <p>Форма та же, что у ошибки HTTP ({@code ru.hothat.dto.common.ErrorDTO}):
 * машинный код и текст для человека. Одна форма на два транспорта избавляет
 * клиента от второй ветки разбора ошибок.
 *
 * <p>Наружу уходит только известный код. Текст произвольного исключения
 * здесь не появляется намеренно: в сообщении JPA или драйвера базы может
 * оказаться содержимое запроса, а получатель кадра — обычный игрок.
 */
@Schema(description = "Отказ канала")
public record ChannelErrorFrameDTO(

        @Schema(description = "Имя кадра", example = "error", allowableValues = "error")
        String type,

        @Schema(description = "Текст отказа из общего словаря; у кода без текста — сам код",
                example = "Требуется вход в аккаунт.")
        String error,

        @Schema(description = "Машинный код отказа", example = "AUTH_REQUIRED")
        String code) {

    public ChannelErrorFrameDTO(String error, String code) {
        this("error", error, code);
    }
}
