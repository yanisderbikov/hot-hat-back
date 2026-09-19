package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Проверка ника на занятость — подсказка формы регистрации.
 *
 * <p>Образца ника здесь намеренно нет, хотя он есть у самой регистрации. Это
 * не забывчивость: адрес отвечает на каждое нажатие клавиши, и «Pe» — ещё не
 * ошибка запроса, а промежуточное состояние поля. Отвергать такое четырёхсотым
 * значило бы отнять у клиента поле {@code valid}, по которому он сегодня и
 * угадывает правило (аудит C12), и заставить его завести десятую копию
 * образца у себя.
 */
@Schema(description = "Параметры проверки ника")
public record NicknameAvailabilityQueryDTO(

        /**
         * Верхний предел взят по колонке {@code app_user.nickname} (40 знаков):
         * запрос длиннее не может описывать ник, который база вообще способна
         * хранить, и разбирать его незачем.
         */
        @Parameter(description = "Проверяемый ник. Правило: латиница, цифры и подчёркивание, "
                + "3–20 знаков, начинается с буквы", example = "petya")
        @NotBlank(message = "Не указан ник.")
        @Size(max = 40, message = "Слишком длинный ник.")
        String nickname) {
}
