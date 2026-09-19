package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Запись партии в админском каталоге.
 *
 * <p>Своя проекция, а не игроцкая {@code RecordingCardView}: там намеренно нет
 * кухни рекордера, а здесь она и есть предмет — по идентификатору Egress и
 * трём отметкам времени видно, на каком шаге запись сорвалась. Общая часть не
 * дублируется руками: обе проекции строит один движок из одной строки.
 */
@Schema(description = "Запись партии в каталоге администратора")
public record AdminRecordingCardView(

        @Schema(description = "Запись", example = "hat-0f3a9c1d7b2e5480-3")
        String recordingId,

        @Schema(description = "Комната, в которой шла партия; null — комнату уже не назвать",
                example = "hat-0f3a9c1d7b2e5480", nullable = true)
        String roomId,

        @Schema(description = "Номер партии внутри комнаты", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Подпись записи: название комнаты, а если его не сохранили — её идентификатор",
                example = "Пятничная шляпа")
        String title,

        @Schema(description = "Режим партии", example = "classic")
        String gameMode,

        @Schema(description = "Партия была рейтинговой", example = "false", type = "boolean")
        boolean ranked,

        @Schema(description = "Комната была закрытой, по приглашению", example = "false", type = "boolean")
        boolean privateRoom,

        @Schema(description = "Комната была тестовой", example = "false", type = "boolean")
        boolean testRoom,

        @Schema(description = "Языковой дивизион комнаты", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Язык слов в партии", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String gameLanguage,

        @Schema(description = "Кто играл")
        List<AdminRecordingParticipantView> participants,

        @Schema(description = "Команды со счётом")
        List<AdminRecordingTeamView> teams,

        @Schema(description = "Стадия жизни записи", example = "complete")
        String status,

        @Schema(description = "Когда начали снимать; null — съёмка не стартовала",
                example = "1788600000000", type = "integer", nullable = true)
        Long startedAtMs,

        @Schema(description = "Когда съёмка кончилась; null — не кончилась", example = "1788603600000",
                type = "integer", nullable = true)
        Long finishedAtMs,

        @Schema(description = "Когда запись удалят; null — она сохранена и хранится",
                example = "1791195600000", type = "integer", nullable = true)
        Long expiresAtMs,

        @Schema(description = "Сколько игроков положили запись к себе", example = "2", type = "integer")
        int savedCount,

        @Schema(description = "Кто именно сохранил: этого нет в игроцкой карточке")
        List<String> savedByUids,

        @Schema(description = "Размер файла в байтах; 0 — файла пока нет", example = "268435456",
                type = "integer")
        long sizeBytes,

        @Schema(description = "Длительность в наносекундах — так её отдаёт LiveKit", example = "912000000000",
                type = "integer")
        long durationNs,

        @Schema(description = "Идентификатор задания Egress; null — задание не создавалось",
                example = "EG_5m7QpZ", nullable = true)
        String egressId,

        @Schema(description = "Когда страница-рекордер доложила о готовности; null — не докладывала",
                example = "1788599990000", type = "integer", nullable = true)
        Long recorderReadyAtMs,

        @Schema(description = "Когда рекордер сообщил о старте съёмки; null — не сообщал",
                example = "1788600000000", type = "integer", nullable = true)
        Long recorderStartSignalAtMs,

        @Schema(description = "Когда Egress подтвердил активность; null — не подтверждал",
                example = "1788600002000", type = "integer", nullable = true)
        Long egressActiveAtMs,

        @Schema(description = "Рекордер прогревался заранее", example = "true", type = "boolean")
        boolean prewarmed,

        @Schema(description = "Секретное слово попало в кадр", example = "true", type = "boolean")
        boolean secretWordRecorded,

        @Schema(description = "Почему съёмка прервалась; null — не прерывалась",
                example = "player-disconnected", nullable = true)
        String terminationReason,

        @Schema(description = "Ошибка старта или выгрузки; null — ошибок не было",
                example = "egress start failed: bucket unreachable", nullable = true)
        String error) {
}
