package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Учётная запись в объёме, который нужен сразу после входа.
 *
 * <p>Общая проекция шести ответов области: пяти выдач сессии и чтения своей
 * учётки. Собирается поле за полем, поэтому {@code passwordHash} и
 * {@code tokenVersion} физически не могут уехать в браузер — прежний
 * документный шлюз сериализовал ту же сущность целиком (аудит A8).
 *
 * <p>Профиля здесь нет: ни аватара, ни дивизиона, ни языка интерфейса. Они
 * живут в {@code GET /api/v2/profile/me} и меняются своими адресами. Раньше
 * вход возвращал их вместе с токенами, и экран регистрации получал дивизион
 * до того, как игрок его выбрал.
 */
@Schema(description = "Учётная запись игрока")
public record AccountView(

        @Schema(description = "Идентификатор игрока: им оперируют комнаты, чаты и рейтинги",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Почта; у гостя её нет", example = "player@example.com", nullable = true)
        String email,

        @Schema(description = "Уникальный ник. У гостя — придуманный сервером Guest######",
                example = "petya", nullable = true)
        String nickname,

        @Schema(description = "Отображаемое имя; по умолчанию совпадает с ником",
                example = "Петя", nullable = true)
        String displayName,

        @Schema(description = "Род учётной записи: от него зависит, что открыто")
        AccountKind kind,

        /**
         * Считает сервер по той же формуле, что и старый вход
         * ({@code AuthServiceImpl.isAdmin}): uid из настроек, почта из настроек
         * либо почта владельца. Клиенту нельзя сравнивать почту у себя — список
         * администраторов лежит в настройках сервера и наружу не отдаётся.
         */
        @Schema(description = "Открыта ли консоль администратора", example = "false")
        boolean admin) {
}
