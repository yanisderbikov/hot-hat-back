package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Версии шести правовых документов, принятых игроком.
 *
 * <p>Общая проекция: одна и та же шестёрка едет в запросе на регистрацию
 * согласия и в ответах о согласиях. Набор документов закрыт — старый сервис
 * перебирал ровно эти шесть ключей и молча выбрасывал любые другие, так что
 * произвольная карта здесь ничего не выражала, кроме возможности опечатки.
 *
 * <p>Версия — дата выпуска документа строкой, как её проставляет фронтенд.
 */
@Schema(description = "Версии принятых правовых документов")
public record ConsentVersionsView(

        @Schema(description = "Версия пользовательского соглашения", example = "2026-08-20")
        @NotBlank(message = "Версия соглашения обязательна.")
        @Size(max = 40)
        String agreement,

        @Schema(description = "Версия политики конфиденциальности", example = "2026-08-20")
        @NotBlank(message = "Версия политики конфиденциальности обязательна.")
        @Size(max = 40)
        String privacy,

        @Schema(description = "Версия согласия на обработку персональных данных", example = "2026-08-20")
        @NotBlank(message = "Версия согласия на обработку данных обязательна.")
        @Size(max = 40)
        String personalData,

        @Schema(description = "Версия правил сообщества", example = "2026-08-20")
        @NotBlank(message = "Версия правил сообщества обязательна.")
        @Size(max = 40)
        String community,

        @Schema(description = "Версия согласия на запись партий", example = "2026-08-20")
        @NotBlank(message = "Версия согласия на запись обязательна.")
        @Size(max = 40)
        String recording,

        @Schema(description = "Версия правил дивизионов", example = "2026-08-19")
        @NotBlank(message = "Версия правил дивизионов обязательна.")
        @Size(max = 40)
        String divisions) {
}
