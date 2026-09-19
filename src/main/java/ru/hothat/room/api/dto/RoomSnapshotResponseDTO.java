package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Комната целиком: паспорт, места, составы и зрители одним ответом.
 *
 * <p>Заменяет четыре живые подписки браузера сразу —
 * {@code rooms/{id}}, {@code rooms/{id}/players}, {@code rooms/{id}/teams} и
 * {@code rooms/{id}/spectators} ({@code app-core.js:8750-8806}). Четырьмя они
 * были не по смыслу, а по устройству прежнего хранилища: экран комнаты не
 * умеет показать состав без команд, а команды без имён игроков, и приезжали
 * они вразнобой — отсюда мигание пустого стола на входе.
 *
 * <p>Живые изменения приходят каналом {@code /ws/v2/room/{roomId}} тем же
 * набором. Этот адрес — первый снимок и починка после разрыва.
 */
@Schema(description = "Снимок комнаты")
public record RoomSnapshotResponseDTO(

        @Schema(description = "Паспорт комнаты")
        RoomSummaryView room,

        @Schema(description = "Места игроков; мёртвые места тоже здесь, с признаком alive=false — "
                + "экран показывает их серыми, а не убирает, иначе стол дёргался бы")
        List<RoomSeatView> players,

        @Schema(description = "Команды в порядке очереди ходов")
        List<RoomTeamView> teams,

        @Schema(description = "Зрители; лёгкие подписки превью с главной сюда не попадают")
        List<RoomSpectatorView> spectators,

        @Schema(description = "Кем спрашивающий сидит в этой комнате", example = "player")
        RoomSeatKind viewerSeat) {
}
