package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.team.api.dto.GameMode;

/**
 * Паспорт комнаты: то, что не меняется от хода к ходу.
 *
 * <p>Общая проекция: включается полем в снимок комнаты, в ответ на создание и
 * в ответ на возврат к настройкам, поэтому форма комнаты описана один раз.
 *
 * <p>Полей ровно столько, сколько нужно экрану комнаты. Всё, что до сих пор
 * приезжало браузеру вместе с документом комнаты — мешок неразыгранных слов,
 * текущее слово чужой команды, голоса апелляции, диверсионные блокировки,
 * состояние тест-ботов, — сюда не попадает: это данные партии, и у них свой
 * адрес {@code GET /api/v2/game/{roomId}}.
 *
 * <p>{@code hostLastSetupActivityAt} назван ключом документа комнаты, а не
 * {@code …AtMs}: экран читает его под этим именем в трёх местах
 * ({@code app-core.js:1089}, {@code :9155}, {@code :13691}), и кадр канала
 * заменяет ему подписку на документ без переименования.
 */
@Schema(description = "Паспорт комнаты")
public record RoomSummaryView(

        @Schema(description = "Идентификатор комнаты; он же ходит по чатам ссылкой-приглашением",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Название комнаты", example = "Комната Васи")
        String name,

        @Schema(description = "Фаза комнаты: по ней экран решает, что показывать и что разрешать",
                example = "setup")
        RoomPhase phase,

        @Schema(description = "Режим партии", example = "classic")
        GameMode gameMode,

        @Schema(description = "Рейтинговая ли комната", example = "false", type = "boolean")
        boolean ranked,

        @Schema(description = "Приватная ли комната: такую не показывают в витрине и не смотрят зрители",
                example = "false", type = "boolean")
        boolean privateRoom,

        @Schema(description = "Тестовая ли комната: в ней сидят боты и включены владельческие поблажки",
                example = "false", type = "boolean")
        boolean testRoom,

        @Schema(description = "Дивизион комнаты: по нему пускают в рейтинговую партию", example = "ru")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Язык слов партии; у быстрой комнаты может отличаться от дивизиона",
                example = "ru")
        DivisionLanguage gameLanguage,

        @Schema(description = "Сколько игроков комната вмещает", example = "10", type = "integer")
        int capacity,

        @Schema(description = "Длительность хода в секундах", example = "60", type = "integer")
        int turnDurationSeconds,

        @Schema(description = "Номер партии внутри комнаты: растёт с каждым стартом и гасит "
                + "приглашения, выданные до него", example = "0", type = "integer")
        int gameNumber,

        @Schema(description = "Просил ли владелец сервиса записывать партии этой комнаты",
                example = "false", type = "boolean")
        boolean recordingRequested,

        @Schema(description = "Кто сейчас хозяин комнаты", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String hostUid,

        @Schema(description = "Когда хозяин в последний раз что-то делал на экране настройки, "
                + "миллисекунды эпохи; 0 — отметок не было. По ней сторож решает, не пора ли "
                + "передать хозяйство", example = "1788600120000", type = "integer")
        long hostLastSetupActivityAt,

        @Schema(description = "Когда комнату завели, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long createdAtMs) {
}
