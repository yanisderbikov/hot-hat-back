package ru.hothat.game.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;

/**
 * Фаза партии.
 *
 * <p>Перечисление вместо строки нужно затем, что сравнений с литералом
 * {@code "active"} в проекте сегодня 124 штуки в двенадцати файлах, и опечатка
 * в любом из них не ломает сборку, а тихо выключает правило. Здесь опечатка
 * перестаёт компилироваться.
 *
 * <p>{@link #code()} — то самое слово, которое лежит в колонке {@code room.phase}
 * и которое читает фронтенд. Оно остаётся неизменным: переезд движка на сервер
 * не должен требовать одновременной правки клиента.
 */
public enum MatchPhase {

    /** Набор: команды, слова, снаряжение. Партии ещё нет. */
    SETUP("setup"),
    /** Карточка «ход команды N»: объясняющий ещё не нажал «начать». */
    TURN_INTRO("turnIntro"),
    /** Ход идёт: у объясняющего есть слово и работают часы. */
    ACTIVE("active"),
    /** Голосование по спорным словам закончившегося хода. */
    APPEAL("appeal"),
    /** Экран итогов хода перед передачей очереди. */
    BETWEEN("between"),
    /** Партия сыграна: шляпа пуста либо засчитано техническое поражение. */
    FINISHED("finished"),
    /** Комната закрыта: партии больше нет и не будет. */
    CLOSED("closed");

    /**
     * Фазы, которые можно ставить на паузу. Ровно те же четыре, что и в
     * {@code GameRules.PAUSABLE_PHASES}: до начала партии паузе нечего
     * останавливать, а после её конца — нечего продолжать.
     */
    public static final Set<MatchPhase> PAUSABLE = Set.of(TURN_INTRO, ACTIVE, APPEAL, BETWEEN);

    private final String code;

    MatchPhase(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public boolean pausable() {
        return PAUSABLE.contains(this);
    }

    /** Партия идёт: место для диверсий, присутствия и пауз. */
    public boolean live() {
        return this == TURN_INTRO || this == ACTIVE || this == APPEAL || this == BETWEEN;
    }

    /**
     * Незнакомое слово превращается в {@link #SETUP}, а не в исключение:
     * значение приезжает из колонки, и партия не должна падать из-за строки,
     * которую туда положила прошлая версия.
     */
    @JsonCreator
    public static MatchPhase of(String value) {
        String normalized = value == null ? "" : value.trim();
        for (MatchPhase phase : values()) {
            if (phase.code.equals(normalized)) {
                return phase;
            }
        }
        return SETUP;
    }
}
