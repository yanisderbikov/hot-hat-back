package ru.hothat.room.usecase;

import ru.hothat.config.ApiException;
import ru.hothat.room.domain.RoomAccessPolicy;
import ru.hothat.room.domain.RoomSeatingPolicy;

/**
 * Отказ домена → код и статус ответа.
 *
 * <p>Одно место на всю область, потому что дисциплина статусов (§10.3 плана)
 * иначе разъезжается: 409 — только настоящий конфликт состояния, 403 — «нет
 * прав», 422 — невыполнимое предусловие при безупречном теле. Пока каждый
 * сценарий выбирал статус сам, «комната полна» приезжала то 409, то 400.
 *
 * <p>Домен статусов не знает намеренно: правило «в команде уже двое» не
 * должно ничего знать про HTTP. Перевод живёт здесь, на границе.
 */
final class RoomRefusals {

    private RoomRefusals() {
    }

    /** Отказ во входе в комнату. */
    static ApiException of(RoomAccessPolicy.Refusal refusal) {
        return switch (refusal) {
            // Комнаты больше нет как площадки — это состояние, а не права.
            case ROOM_CLOSED -> ApiException.of("ROOM_CLOSED", 409);
            // Дивизион — это право играть здесь, а не занятость мест.
            case RANKED_DIVISION_MISMATCH -> ApiException.of("RANKED_DIVISION_MISMATCH", 403);
            case ROOM_DIVISION_MISMATCH -> ApiException.of("ROOM_DIVISION_MISMATCH", 403);
            case ROOM_FULL -> ApiException.of("ROOM_FULL", 409);
            case GAME_ALREADY_STARTED -> ApiException.of("GAME_ALREADY_STARTED", 409);
            case PRIVATE_GAME_STARTED -> ApiException.of("PRIVATE_GAME_STARTED", 403);
            case PRIVATE_ROOM_NOT_WATCHABLE -> ApiException.of("PRIVATE_ROOM_NOT_WATCHABLE", 403);
            case SPECTATORS_AFTER_START -> ApiException.of("SPECTATORS_AFTER_START", 409);
            // Место уже есть — за столом; это состояние просящего, а не права.
            case PLAYER_CANNOT_WATCH -> ApiException.of("PLAYER_CANNOT_WATCH", 409);
        };
    }

    /** Отказ в правке составов. */
    static ApiException of(RoomSeatingPolicy.Refusal refusal) {
        return switch (refusal) {
            case SETUP_ONLY -> ApiException.of("ROOM_SETUP_ONLY", 409);
            case TEAM_LIMIT_REACHED -> ApiException.of("TEAM_LIMIT_REACHED", 409);
            case TEAM_NAME_TAKEN -> ApiException.of("ROOM_TEAM_NAME_TAKEN", 409);
            case TEAM_IS_FULL -> ApiException.of("TEAM_IS_FULL", 409);
            case TEAM_NOT_EMPTY -> ApiException.of("TEAM_NOT_EMPTY", 409);
            case TEAMS_NOT_READY -> ApiException.of("TEAMS_NOT_READY", 409);
            // Тело безупречно, но выполнить нечего: живых игроков в комнате нет.
            case NO_ACTIVE_PLAYERS -> ApiException.of("NO_ACTIVE_PLAYERS", 422);
        };
    }
}
