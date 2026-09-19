package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.Nickname;

/** Заявка в друзья игроку, названному ником. */
@Schema(description = "Запрос на добавление в друзья по нику")
public record InviteFriendByNicknameRequestDTO(

        /**
         * Формат тот же, что проверяет {@code Ids.NICKNAME}: заявка с мусором
         * вместо ника не должна доходить до поиска по индексу.
         */
        @Schema(description = "Ник адресата; регистр не важен, пробелы по краям недопустимы",
                example = "vasya", pattern = "^[A-Za-z][A-Za-z0-9_]{2,19}$")
        @NotBlank(message = "Укажите ник игрока.")
        @Nickname
        String nickname) {
}
