package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.room.api.dto.RoomSeatKind;
import ru.hothat.room.api.dto.RoomSeatView;
import ru.hothat.room.api.dto.RoomSpectatorView;
import ru.hothat.room.api.dto.RoomSummaryView;
import ru.hothat.room.api.dto.RoomTeamView;

import java.util.List;

/**
 * Комната целиком — та же пятёрка полей, что у {@code GET /api/v2/room/{roomId}}.
 *
 * <p>Поля повторяют {@code RoomSnapshotResponseDTO} один в один, а вложенные
 * проекции — те же самые записи области комнаты. Экран комнаты рисует состав
 * одним и тем же кодом, пришёл он снимком по HTTP после переподключения или
 * кадром по каналу.
 */
@Schema(description = "Снимок комнаты в кадре канала")
public record RoomSnapshotView(

        @Schema(description = "Паспорт комнаты")
        RoomSummaryView room,

        @Schema(description = "Места игроков; мёртвые места тоже здесь, с признаком alive=false")
        List<RoomSeatView> players,

        @Schema(description = "Команды в порядке очереди ходов")
        List<RoomTeamView> teams,

        @Schema(description = "Зрители; лёгкие подписки превью с главной сюда не попадают")
        List<RoomSpectatorView> spectators,

        @Schema(description = "Кем слушатель сидит в этой комнате", example = "player")
        RoomSeatKind viewerSeat) {
}
