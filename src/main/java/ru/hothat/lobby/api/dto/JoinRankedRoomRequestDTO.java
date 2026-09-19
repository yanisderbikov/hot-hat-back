package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.hothat.team.api.dto.GameMode;

/**
 * Ввод рейтинговой пары в названную комнату.
 *
 * <p>Отличается от заявки на подбор тем, что комната уже выбрана: пара
 * договорилась на предматчевой проверке и идёт именно туда. Режим всё равно
 * называется явно — движок сверяет его с режимом комнаты и с режимом
 * подготовки, и расхождение здесь означает, что пара идёт не в ту лигу.
 */
@Schema(description = "Запрос на вход рейтинговой пары в комнату")
public record JoinRankedRoomRequestDTO(

        @Schema(description = "Режим партии, о котором договорилась пара", example = "sabotage")
        @NotNull(message = "Нужен режим партии.")
        GameMode gameMode) {
}
