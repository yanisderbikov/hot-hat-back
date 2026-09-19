package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ подсказки: подходит ли ник по форме и свободен ли он.
 *
 * <p>Два поля, а не одно, потому что причины отказа разные и лечатся по-разному:
 * «Pe» надо дописать, а «petya» — заменить. Слитый признак заставлял бы форму
 * показывать одно сообщение на оба случая.
 */
@Schema(description = "Занятость ника")
public record NicknameAvailabilityResponseDTO(

        @Schema(description = "Ник в том виде, в каком его проверили: обрезанный по краям",
                example = "petya")
        String nickname,

        @Schema(description = "Подходит ли ник по форме", example = "true")
        boolean valid,

        /**
         * У неподходящего по форме ника здесь всегда {@code false}: свободен он
         * или нет, занять его всё равно нельзя, и отвечать «свободен» значило бы
         * обещать невозможное.
         */
        @Schema(description = "Свободен ли ник. У ника, не прошедшего по форме, всегда false",
                example = "true")
        boolean available) {
}
