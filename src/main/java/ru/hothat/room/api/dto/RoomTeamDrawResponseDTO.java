package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Итог жеребьёвки.
 *
 * <p>Кнопка намеренно не хозяйская: перемешать состав может любой участник
 * ({@code app-core.js:11843}). Это не упущение прав, а правило — в комнате
 * незнакомых людей право пересадить всех не должно принадлежать одному.
 *
 * <p>Каждый вызов — полностью новая жеребьёвка, включая уже рассаженных:
 * половинчатое перемешивание, которое трогает только свободных, выглядело бы
 * как поломка кнопки.
 */
@Schema(description = "Итог жеребьёвки составов")
public record RoomTeamDrawResponseDTO(

        @Schema(description = "Составы после жеребьёвки, в порядке очереди ходов")
        List<RoomTeamView> teams,

        @Schema(description = "Сколько игроков село за стол", example = "8", type = "integer")
        int seated,

        @Schema(description = "Сколько осталось без команды: мест меньше, чем желающих",
                example = "1", type = "integer")
        int benched) {
}
