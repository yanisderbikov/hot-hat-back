package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Запись одной партии так, как её видит игрок.
 *
 * <p>Общая проекция плана (§10.1): включается полем в библиотеку записей, в
 * ответ на сохранение и в состояние записи текущей партии. Форма описана один
 * раз, различаются обёртки.
 *
 * <p>Это игроцкая проекция, и она уже, чем сегодняшняя строка
 * {@code RecordingServiceImpl.publicRow} из 32 ключей. Наружу не выходит
 * кухня рекордера — {@code egressId}, {@code recorderReadyAtMs},
 * {@code recorderStartSignalAtMs}, {@code egressActiveAtMs},
 * {@code prewarmed}, {@code secretWordRecorded}, {@code terminationReason}:
 * ни один экран профиля их не читает, а знать про Egress игроку незачем.
 * Админский каталог получит их своей проекцией.
 *
 * <p>Времена — миллисекунды эпохи, как во всём остальном API. Ноль как
 * «ещё не случилось» заменён на {@code null}: сегодня клиент отличает
 * незавершённую запись по {@code finishedAtMs === 0} ({@code portal.js:65}),
 * а ноль — это 1970 год, а не «нет значения».
 */
@Schema(description = "Карточка записи партии")
public record RecordingCardView(

        @Schema(description = "Запись: по этому идентификатору берутся ссылки на просмотр",
                example = "hat-0f3a9c1d7b2e5480-3")
        String recordingId,

        @Schema(description = "Комната, в которой шла партия; null — комнату уже не назвать",
                example = "hat-0f3a9c1d7b2e5480", nullable = true)
        String roomId,

        @Schema(description = "Номер партии внутри комнаты: за вечер их бывает несколько",
                example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Подпись записи: название комнаты, а если его не сохранили — её идентификатор",
                example = "Пятничная шляпа")
        String title,

        @Schema(description = "Режим партии", example = "classic", allowableValues = {"classic", "sabotage"})
        String gameMode,

        @Schema(description = "Партия была рейтинговой", example = "false", type = "boolean")
        boolean ranked,

        @Schema(description = "Комната была закрытой, по приглашению", example = "false", type = "boolean")
        boolean privateRoom,

        @Schema(description = "Комната была тестовой: такие записи не показывают достижений",
                example = "false", type = "boolean")
        boolean testRoom,

        @Schema(description = "Языковой дивизион комнаты", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Язык слов в партии; обычно совпадает с дивизионом", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String gameLanguage,

        @Schema(description = "Кто играл — снимок состава на момент партии")
        List<RecordingParticipantView> participants,

        @Schema(description = "Команды со счётом и признаком победы")
        List<RecordingTeamView> teams,

        @Schema(description = "Счёт победителя", example = "24", type = "integer")
        int winningScore,

        @Schema(description = "Сумма очков всех команд", example = "41", type = "integer")
        int totalScore,

        @Schema(description = "Сколько слов успели объяснить за партию", example = "87", type = "integer")
        int wordCount,

        @Schema(description = "Стадия жизни записи")
        RecordingStatus status,

        @Schema(description = "Есть ли что смотреть прямо сейчас. Считает сервер: сегодня это "
                + "решение принимают два места клиента, сравнивая статус со строкой \"complete\"",
                example = "true", type = "boolean")
        boolean playable,

        @Schema(description = "Когда начали снимать; null — съёмка ещё не стартовала",
                example = "1788600000000", type = "integer", nullable = true)
        Long startedAtMs,

        @Schema(description = "Когда съёмка кончилась; null — партия ещё идёт или запись не завершилась",
                example = "1788603600000", type = "integer", nullable = true)
        Long finishedAtMs,

        @Schema(description = "Когда запись удалят; null — она сохранена и хранится, пока её не забрать "
                + "из библиотеки", example = "1791195600000", type = "integer", nullable = true)
        Long expiresAtMs,

        @Schema(description = "Сколько игроков положили запись к себе", example = "2", type = "integer")
        int savedCount,

        @Schema(description = "Размер файла в байтах; 0 — файла пока нет", example = "268435456",
                type = "integer")
        long sizeBytes,

        @Schema(description = "Длительность записи в наносекундах — так её отдаёт LiveKit; 0 — файла пока нет",
                example = "912000000000", type = "integer")
        long durationNs,

        @Schema(description = "Почему запись не получилась; null — ошибки не было",
                example = "egress start failed: bucket unreachable", nullable = true)
        String error) {
}
