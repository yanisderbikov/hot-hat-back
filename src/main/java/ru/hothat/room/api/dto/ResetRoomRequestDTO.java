package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Насколько глубоко пересобрать комнату.
 *
 * <p>Тела у этого адреса по плану не было: две клиентские процедуры сводились
 * в одну без различия. Различие пришлось вернуть, потому что оно
 * продуктовое, а не техническое. {@code backToSetup()} обнуляет счёт и
 * оставляет команды — это «сыграем ещё раз тем же составом», самый частый
 * конец вечера. {@code resetRoom()} распускает команды и выбрасывает сданные
 * слова — это «собираемся заново». Один сценарий без флага молча делал бы
 * второе там, где люди ждали первого, и заново рассаживал бы восьмерых.
 */
@Schema(description = "Глубина пересборки комнаты")
public record ResetRoomRequestDTO(

        @Schema(description = "Распустить команды и выбросить сданные слова. По умолчанию нет: "
                + "счёт обнуляется, составы остаются", example = "false", type = "boolean",
                nullable = true)
        Boolean clearTeams) {

    public boolean clearTeamsOrDefault() {
        return Boolean.TRUE.equals(clearTeams);
    }
}
