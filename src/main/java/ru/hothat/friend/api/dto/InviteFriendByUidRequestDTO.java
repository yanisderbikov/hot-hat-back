package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.PlayerUid;

/**
 * Заявка в друзья игроку, которого уже видно на экране: соседу по комнате
 * ({@code app-core.js:14618}) или строке из консоли администратора
 * ({@code friends/friends.js:15}).
 */
@Schema(description = "Запрос на добавление в друзья по идентификатору игрока")
public record InviteFriendByUidRequestDTO(

        @Schema(description = "Идентификатор адресата", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk",
                pattern = "^[A-Za-z0-9_-]{1,160}$")
        @NotBlank(message = "Укажите игрока.")
        @PlayerUid
        String friendUid,

        /**
         * Подсказка на случай, когда у адресата нет ни ника в профиле, ни
         * записи в индексе: тогда в заявке сохранится имя, которое видел
         * отправитель, а не машинное «playerXXXXXX».
         */
        @Schema(description = "Как адресат подписан на экране отправителя; можно не задавать",
                example = "vasya", nullable = true)
        String nicknameHint) {
}
