package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Приглашения в команды, ждущие ответа.
 *
 * <p>Отдельный адрес, а не поле в ответе своей команды: приглашение приходит
 * тому, у кого команды ещё нет, и раньше страница портала спрашивала всю свою
 * команду со статистикой и префлайтом ради одного массива на десять строк
 * ({@code portal.js:115}).
 */
@Schema(description = "Входящие приглашения в команды")
public record TeamInvitesResponseDTO(

        @Schema(description = "Приглашения в порядке, в котором их отдаёт хранилище")
        List<TeamInviteView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт "
                + "первый десяток и вглубь не ходит", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этому ответу", example = "10", type = "integer")
        int limit) {
}
