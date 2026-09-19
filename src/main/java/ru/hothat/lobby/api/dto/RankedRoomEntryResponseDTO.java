package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Рейтинговая пара вошла в названную комнату.
 *
 * <p>Заменяет {@code portal ranked_join_room}. Ответ не заявка: комната здесь
 * не подбирается, а названа заранее — на предматчевой проверке, — поэтому и
 * состояния поиска у этой операции нет.
 */
@Schema(description = "Результат входа рейтинговой пары в комнату")
public record RankedRoomEntryResponseDTO(

        @Schema(description = "Комната, в которую вошла пара", example = "hat-0f3a9c1d7b2e5480",
                pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Готова ли комната принять пару прямо сейчас. Всегда true: движок "
                + "не возвращает управление, пока пара не села за стол", example = "true", type = "boolean")
        boolean ready) {
}
