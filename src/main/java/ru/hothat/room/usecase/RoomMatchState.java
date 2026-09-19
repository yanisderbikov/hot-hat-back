package ru.hothat.room.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.model.room.Room;
import ru.hothat.room.domain.RoomPhase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Стереть в комнате всё, что относилось к сыгранной партии.
 *
 * <p>Отдельный класс, а не приватный метод сценария, потому что перечень
 * должен быть один. Пока он лежал копиями, они успели разойтись: клиентский
 * {@code backToSetup()} чистил двадцать четыре поля, {@code resetRoom()} —
 * двадцать восемь, а {@code prepareGame()} на сервере — свои шесть. Любое
 * забытое поле переживает возврат к настройкам и всплывает в следующей
 * партии: застрявшая пауза, чужое слово на экране, голоса апелляции, которой
 * не было.
 *
 * <p>Класс публичен, потому что им пользуется соседний пакет области. Наружу
 * области он не выходит.
 */
@Component
public class RoomMatchState {

    /**
     * Комната возвращается к набору.
     *
     * @param teamOrder очередь ходов после возврата; пусто — команды распущены
     */
    public void clear(Room room, List<String> teamOrder) {
        room.setPhase(RoomPhase.SETUP.wireValue());
        clearPause(room);

        room.setTeamRosters(new LinkedHashMap<>());
        room.setGamePlayerNamesByUid(new LinkedHashMap<>());
        room.setBag(new ArrayList<>());
        room.setWordsLeft(0);
        room.setCurrentTeamIndex(0);
        room.setCurrentTeamId(teamOrder.isEmpty() ? null : teamOrder.get(0));

        room.setCurrentWord(null);
        room.setCurrentTurnScore(0);
        room.setExplainerUid(null);
        room.setExplainerName(null);
        room.setGuesserUid(null);
        room.setGuesserName(null);
        room.setTurnStartedAt(null);
        // Длительность идущего хода, а не настройка комнаты: настройка живёт в
        // соседней колонке и переживает возврат к набору.
        room.setTurnDurationSeconds(null);
        room.setTurnEndsAt(0L);
        room.setTurnId(null);
        room.setTurnGuessedWords(new ArrayList<>());
        room.setLastGuessedWord(null);
        room.setLastSkippedWord(null);
        room.setLastTurn(null);

        room.setAppealEndsAt(0L);
        room.setAppealVotes(new LinkedHashMap<>());

        room.setSabotageEvent(null);
        room.setSabotageCooldownUntil(0L);
        room.setSabotageLocks(new LinkedHashMap<>());
        room.setReplacementRecordings(new LinkedHashMap<>());
        room.setSpecialRewardProgressByTeam(new LinkedHashMap<>());
        room.setSpecialRewardCursorByTeam(new LinkedHashMap<>());

        room.setTechnicalTermination(null);
        room.setLastActivityAt(System.currentTimeMillis());
    }

    /**
     * Снять паузу и всё, что её описывает.
     *
     * <p>Отдельным методом, потому что старт партии обязан делать то же самое:
     * комната, начавшая игру с застрявшим признаком паузы, показывала бы
     * заставку «ждём возвращения игрока» поверх первого же хода.
     */
    public void clearPause(Room room) {
        room.setGamePaused(false);
        room.setHostPaused(false);
        room.setPauseReason(null);
        room.setPauseMissingUids(new ArrayList<>());
        room.setPauseMissingNames(new ArrayList<>());
        room.setPauseStartedAtMs(0L);
        room.setPausedTurnRemainingMs(0L);
        room.setPausedAppealRemainingMs(0L);
    }
}
