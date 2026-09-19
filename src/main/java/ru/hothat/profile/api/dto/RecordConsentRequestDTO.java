package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Зарегистрировать принятие правовых документов. */
@Schema(description = "Запрос на регистрацию согласий")
public record RecordConsentRequestDTO(

        @Schema(description = "Версии документов, которые игрок принял")
        @NotNull(message = "Версии документов обязательны.")
        @Valid
        ConsentVersionsView versions,

        /**
         * Отдельное поле, а не седьмая «версия»: подтверждение возраста — факт
         * о человеке, а не о документе, и хранится оно в отдельной колонке.
         */
        @Schema(description = "Подтверждение совершеннолетия", example = "true", type = "boolean")
        @NotNull(message = "Подтверждение возраста обязательно.")
        Boolean adultConfirmed) {
}
