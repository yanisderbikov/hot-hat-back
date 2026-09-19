package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.machine.api.dto.RecorderRoomStateView;
import ru.hothat.machine.api.dto.RecorderSceneState;
import ru.hothat.machine.api.dto.RecorderTeamView;

import java.util.List;

/**
 * Зеркало сцены — то, из чего страница записи собирает кадр видео.
 *
 * <p>Общая проекция обоих кадров канала {@code /ws/v2/machine/recorder/rooms/{roomId}}.
 *
 * <p>Поля повторяют {@code RecorderRoomStateResponseDTO} один в один и
 * дополнены составами — тем же {@link RecorderTeamView}, что отдаёт сцена.
 * Так закрываются обе подписки рекордера сразу: документ комнаты и её команды
 * ({@code app-core.js:13913}, {@code :13940}). Разделять их незачем: без счёта
 * команд кадр всё равно не нарисовать, а приезжали они вразнобой — отсюда
 * секунда записи со старым счётом.
 *
 * <p>Запасной путь остаётся прежним: если канал не поднялся, страница берёт то
 * же самое запросом {@code GET …/room-state}. Форма одна на оба пути — иначе
 * видео, снятое по запасному пути, отличалось бы от снятого по каналу.
 *
 * <p>Мешок слов и пул сюда не попадают, а текущее слово — попадает: запись
 * намеренно показывает то, что объясняли.
 */
@Schema(description = "Зеркало сцены для страницы записи")
public record RecorderMirrorView(

        @Schema(description = "Совпала ли снимаемая партия с текущей партией комнаты")
        RecorderSceneState state,

        @Schema(description = "Комната съёмки", example = "hat-3f1c9a2b7d4e6501")
        String roomId,

        @Schema(description = "Номер снимаемой партии", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Фаза комнаты", example = "active",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "between", "finished", "closed"})
        String phase,

        @Schema(description = "Состояние комнаты; null при state=STALE_GAME", nullable = true)
        RecorderRoomStateView roomState,

        @Schema(description = "Команды партии со счётом и составом; пусто при state=STALE_GAME")
        List<RecorderTeamView> teams,

        @Schema(description = "Серверное время, мс эпохи: по нему страница правит свои часы",
                example = "1757068800000", type = "integer", format = "int64")
        long serverNowMs) {
}
