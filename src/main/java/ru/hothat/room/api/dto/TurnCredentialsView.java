package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Короткоживущая учётка TURN, приложенная к видеотокену.
 *
 * <p>Вложенный блок, а не соседние поля ответа: TURN может быть не настроен, и
 * тогда пусто всё сразу. Три поля по отдельности пришлось бы проверять
 * поодиночке, и клиент, увидевший адреса без пароля, попытался бы соединиться.
 *
 * <p>Пароль — это HMAC от имени на общем секрете, и живёт он ровно столько,
 * сколько сказано в {@code ttlSeconds}: пересылать его дальше бессмысленно.
 */
@Schema(description = "Учётка TURN на время сеанса")
public record TurnCredentialsView(

        @Schema(description = "Адреса TURN-серверов",
                example = "[\"turn:turn.hot-hat.ru:3478?transport=udp\"]")
        List<String> urls,

        @Schema(description = "Имя пользователя вида «срок:участник»",
                example = "1788607200:Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String username,

        @Schema(description = "Пароль сеанса", example = "8yFq0v0mQ2m3Zk1sV7T9pB0oXyE=")
        String credential,

        @Schema(description = "Сколько секунд учётка действительна", example = "7200", type = "integer")
        long ttlSeconds) {
}
