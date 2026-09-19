package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.team.api.dto.GameMode;

/**
 * Комната так, как её рисует строка витрины.
 *
 * <p>Полей ровно столько, сколько рисует экран. Всё, по чему
 * {@code home/home.js:98} фильтровал список у себя — {@code closedAt},
 * {@code isPrivate}, {@code managedMatchmaking}, {@code matchmakingReady},
 * {@code testOwnerUid} — сюда не попадает: отбор выполнил сервер, и держать
 * в ответе входные данные чужого решения незачем. Раньше они приезжали
 * потому, что подписка отдавала документ комнаты целиком — вместе с мешком
 * слов, составами, голосами апелляции и текущим словом.
 *
 * <p>Признака «моя тестовая комната» тоже нет: чужие тестовые комнаты в
 * витрину не попадают вовсе, поэтому {@code testRoom: true} и означает «моя».
 */
@Schema(description = "Строка витрины открытых комнат")
public record LobbyRoomCardView(

        @Schema(description = "Идентификатор комнаты; он же показан игроку и годится для входа по ID",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Название комнаты; пустое — комнату не назвали",
                example = "Комната Васи", nullable = true)
        String name,

        @Schema(description = "Фаза комнаты: по ней экран решает, кнопка это «войти» или «смотреть»",
                example = "active")
        LobbyRoomPhase phase,

        @Schema(description = "Режим партии", example = "classic")
        GameMode gameMode,

        @Schema(description = "Рейтинговая ли комната", example = "false", type = "boolean")
        boolean ranked,

        @Schema(description = "Язык слов в партии: по нему идёт отбор по дивизиону в витрине",
                example = "ru", allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String gameLanguage,

        @Schema(description = "Дивизион комнаты; у быстрой комнаты может отличаться от языка партии",
                example = "ru", allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Сколько игроков комната вмещает", example = "10", type = "integer")
        int maxPlayers,

        @Schema(description = "Сколько игроков в ней сейчас: счёт ведёт сервер, а не браузер",
                example = "6", type = "integer")
        int activePlayers,

        @Schema(description = "Тестовая ли это комната. В витрине бывает только своя собственная",
                example = "false", type = "boolean")
        boolean testRoom) {
}
