package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;

import java.util.List;

/**
 * Снаряжение рекордера: всё, что нужно, чтобы открыть сцену и начать съёмку.
 *
 * <p>Заменяет режим {@code GET /api/recording-state?bootstrap=1}. Ответ тот же
 * по составу, но без служебных {@code ok} и {@code recorder}: первое ничего не
 * значит при коде 201, второе повторяет адрес.
 */
@Schema(description = "Снаряжение рекордера перед съёмкой партии")
public record RecorderSessionResponseDTO(

        @Schema(description = "Токен, с которым рекордер открывает страницу партии",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJob3QtaGF0LXJlY29yZGVyIn0.sig")
        String recorderToken,

        @Schema(description = "Съёмка начата заранее, партия ещё не стартовала", example = "true",
                type = "boolean")
        boolean prewarm,

        @Schema(description = "Комната съёмки", example = "hat-3f1c9a2b7d4e6501")
        String roomId,

        @Schema(description = "Номер снимаемой партии", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Фаза комнаты на момент снаряжения", example = "setup",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "between", "finished", "closed"})
        String phase,

        @Schema(description = "Язык, на котором рисовать подписи в записи")
        DivisionLanguage gameLanguage,

        @Schema(description = "Состояние комнаты целиком")
        RecorderRoomStateView roomState,

        @Schema(description = "Команды комнаты")
        List<RecorderTeamView> teams,

        @Schema(description = "Состав комнаты")
        List<RecorderRosterPlayerView> players,

        @Schema(description = "Серверное время, мс эпохи: у рекордера нет строки игрока, "
                + "поэтому обычную сверку часов он сделать не может",
                example = "1757068800000", type = "integer", format = "int64")
        long serverNowMs) {
}
