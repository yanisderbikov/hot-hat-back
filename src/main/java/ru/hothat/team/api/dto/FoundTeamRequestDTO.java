package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.hothat.common.validation.Nickname;

/**
 * Основать команду и позвать в неё напарника.
 *
 * <p>Дивизиона в запросе нет: команда наследует его у основателя, и позволить
 * задать его отдельно значило бы создавать команды в чужой лиге. Напарник
 * назван ником, а не идентификатором, потому что зовут его с экрана друзей,
 * где видно имя.
 */
@Schema(description = "Запрос на основание рейтинговой команды")
public record FoundTeamRequestDTO(

        /**
         * Правило повторено здесь выражением, а не отдельной аннотацией
         * {@code @TeamName}: общий пакет проверок принадлежит не этой области,
         * и заводить в нём формат из области команды нельзя. Выражение — то
         * же самое, что проверяет {@code Ids.TEAM_NAME} внутри движка.
         */
        @Schema(description = "Название команды: латиница, цифры, пробел, дефис и подчёркивание, 3–30 знаков",
                example = "Hat Wolves", pattern = "^[A-Za-z0-9][A-Za-z0-9 _-]{2,29}$")
        @NotBlank(message = "Нужно название команды.")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 _-]{2,29}$", message = "Некорректное название команды.")
        String name,

        @Schema(description = "Ник напарника. Он должен быть вашим другом и играть в том же дивизионе",
                example = "petya", pattern = "^[A-Za-z][A-Za-z0-9_]{2,19}$")
        @NotBlank(message = "Нужен ник напарника.")
        @Nickname
        String partnerNickname,

        /**
         * Раньше слишком большой логотип молча обрезался до 280 000 знаков уже
         * после сохранения — команда получала битую картинку и узнавала об
         * этом, увидев её на своей карточке. Теперь предел объявлен и
         * нарушение видно сразу.
         */
        @Schema(description = "Логотип команды как data-URL; можно не передавать",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ", maxLength = 280000, nullable = true)
        @Size(max = 280000, message = "Логотип слишком большой.")
        String logoDataUrl) {
}
