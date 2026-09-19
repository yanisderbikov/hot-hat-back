package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Игрок, которому ушла заявка.
 *
 * <p>Общая проекция двух способов позвать в друзья: по нику и по
 * идентификатору. Форма адресата описана один раз, различаются обёртки.
 * Ник здесь разрешённый, а не тот, что набрал отправитель: по нику
 * {@code VASYA} заявка уйдёт игроку {@code vasya}, и клиент должен показать
 * второе.
 */
@Schema(description = "Адресат заявки в друзья")
public record InvitedPlayerView(

        @Schema(description = "Идентификатор адресата", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk",
                pattern = "^[A-Za-z0-9]{28}$")
        String uid,

        @Schema(description = "Разрешённый ник адресата", example = "vasya")
        String nickname) {
}
