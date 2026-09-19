package ru.hothat.room.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * Крупная фаза комнаты: она решает, что в комнате сейчас можно делать.
 *
 * <p>Живёт в домене, а не среди DTO, потому что на неё опирается каждое
 * правило этой области: «команды меняют только до начала игры», «зритель
 * заходит только после начала», «хозяйство передают только в наборе». Пока
 * фаза была строкой, эти условия писались сравнением с литералом
 * {@code "setup"} — в клиенте девять раз, на сервере семь, — и любая опечатка
 * молча открывала действие не вовремя.
 *
 * <p>{@code @JsonValue} здесь не нарушает чистоту домена: это аннотация
 * сериализации, а не Spring и не база. Она нужна, чтобы наружу поехало
 * {@code "turnIntro"}, а не {@code "TURN_INTRO"}: колонка {@code room.phase}
 * и весь фронтенд знают только строчную форму, и переименовать её значило бы
 * ломать обе стороны ради имени константы.
 */
public enum RoomPhase {

    /** Набор: игроки собираются, команды и слова ещё меняют. */
    SETUP("setup"),
    /** Заставка перед ходом: пара названа, время ещё не пошло. */
    TURN_INTRO("turnIntro"),
    /** Идёт ход. */
    ACTIVE("active"),
    /** Апелляция по спорным словам. */
    APPEAL("appeal"),
    /** Пауза между ходами. */
    BETWEEN("between"),
    /** Партия доиграна, комната ещё жива. */
    FINISHED("finished"),
    /** Комнату пересобирают: команды и слова уже стёрты, набор ещё не открыт. */
    RESETTING("resetting"),
    /** Комната закрыта хозяином, админом или техзавершением. */
    CLOSED("closed");

    private final String wireValue;

    RoomPhase(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Фаза из колонки. Незнакомое значение — это {@link #SETUP}, а не отказ:
     * комната старого поколения не должна становиться недоступной из-за
     * строки, которой в перечислении не оказалось. Все опасные действия
     * всё равно спрашивают {@link #isSetup()} явно.
     */
    public static RoomPhase fromWire(String value) {
        return match(value).orElse(SETUP);
    }

    public static Optional<RoomPhase> match(String value) {
        for (RoomPhase phase : values()) {
            if (phase.wireValue.equals(value)) {
                return Optional.of(phase);
            }
        }
        return Optional.empty();
    }

    /** Набор: единственная фаза, в которой меняют составы, слова и хозяйство. */
    public boolean isSetup() {
        return this == SETUP;
    }

    /**
     * Идёт партия. {@code FINISHED} сюда не входит намеренно: итоги уже
     * подведены, и вход в комнату после них разрешён так же, как в наборе.
     */
    public boolean isLive() {
        return this == TURN_INTRO || this == ACTIVE || this == APPEAL || this == BETWEEN;
    }
}
