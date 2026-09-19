package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import ru.hothat.team.api.dto.GameMode;

/**
 * Комната крупным планом: то, что показывает превью на главной.
 *
 * <p>Заменяет четыре живые подписки сразу: три на состав выбранной комнаты
 * ({@code home/home.js:88} — игроки, зрители, команды) и одну на документ
 * игрока ({@code live-preview.js:34}).
 *
 * <p>Зрителей среди полей нет, и это не потеря. Подписка на них существовала,
 * но её результат не рисовался нигде: {@code state.details.spectators}
 * заполнялся и ни разу не читался при отрисовке. Отдавать список,
 * который никто не показывает, значило бы платить запросом за пустоту.
 *
 * <p>Текущего слова здесь нет и быть не может. Наружу выходит только
 * {@code lastGuessedWord} — уже отгаданное; пропущенное слово остаётся
 * секретом, как и то, которое объясняют прямо сейчас. Сегодня это правило
 * держится на комментарии в клиенте ({@code home/home.js:63}), то есть на
 * доброй воле того, кто читает подписку; здесь его держит форма ответа.
 */
@Schema(description = "Комната крупным планом для превью")
public record LobbyRoomPreviewResponseDTO(

        @Schema(description = "Идентификатор комнаты", example = "hat-0f3a9c1d7b2e5480",
                pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Название комнаты", example = "Комната Васи", nullable = true)
        String name,

        @Schema(description = "Фаза комнаты", example = "active")
        LobbyRoomPhase phase,

        @Schema(description = "Режим партии", example = "sabotage")
        GameMode gameMode,

        @Schema(description = "Рейтинговая ли комната", example = "false", type = "boolean")
        boolean ranked,

        @Schema(description = "Язык слов в партии", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String gameLanguage,

        @Schema(description = "Сколько игроков комната вмещает", example = "10", type = "integer")
        int maxPlayers,

        @Schema(description = "Команда, которая ходит сейчас; null — ход ещё не начался",
                example = "team-1", nullable = true)
        String currentTeamId,

        @Schema(description = "Кто из ходящей команды сидит за столом: двое, объясняющий первым. "
                + "Считает сервер — по составу хода, а при его отсутствии по команде игрока",
                example = "[\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\", \"Rm7yBcUv2nQpS4tWxZyB3dFgHiJl\"]")
        List<String> currentTeamMemberUids,

        @Schema(description = "Кто объясняет; null — ход не идёт",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk", nullable = true)
        String explainerUid,

        @Schema(description = "Имя объясняющего", example = "vasya", nullable = true)
        String explainerName,

        @Schema(description = "Кто отгадывает; null — ход не идёт",
                example = "Rm7yBcUv2nQpS4tWxZyB3dFgHiJl", nullable = true)
        String guesserUid,

        @Schema(description = "Последнее отгаданное слово — единственное слово, которое видно зрителю. "
                + "null — в этом ходу ещё не отгадали ни одного", example = "паровоз", nullable = true)
        String lastGuessedWord,

        @Schema(description = "Команды комнаты в порядке ходов")
        List<LobbyTeamView> teams,

        @Schema(description = "Игроки комнаты, которых видели за последние четыре минуты")
        List<LobbyPlayerView> players,

        @Schema(description = "Действующая диверсия; null — сейчас ничего не летит", nullable = true)
        LobbySabotageEffectView sabotage) {
}
