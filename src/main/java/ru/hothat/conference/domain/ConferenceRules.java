package ru.hothat.conference.domain;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Правила видео-чата: пределы, срок и жизненный цикл участия.
 *
 * <p>Чистый класс без Spring и без базы, как {@code room.domain.RoomAccessPolicy}:
 * числа и переходы состояний названы один раз и здесь, а сценарии их только
 * применяют. Раньше те же пределы были рассыпаны по шести файлам страницы
 * в браузере и трём функциям на Vercel — и у каждого была своя копия.
 */
public final class ConferenceRules {

    private ConferenceRules() {
    }

    /** Форма идентификатора: как у комнаты, но со своим префиксом. */
    public static final Pattern ID = Pattern.compile("^vc-[a-f0-9]{16}$");

    /** Столько человек помещается в один звонок. */
    public static final int MAX_PARTICIPANTS = 16;

    /**
     * Столько мест в игровой комнате: видео-чат вмещает больше, чем стол, и
     * «создать комнату этим составом» отказывает, пока в звонке лишние.
     */
    public static final int MAX_GAME_PLAYERS = 10;

    /** Видео-чат не закрывают руками — он истекает. */
    public static final Duration TTL = Duration.ofHours(12);

    /** Столько знаков текста помещается в поле ввода чата. */
    public static final int MAX_TEXT_LENGTH = 1000;

    /** Столько сообщений видит открывший чат; более ранние не листаются. */
    public static final int CHAT_WINDOW = 120;

    /** Предел вложения: тот же, что стоит ограничением базы. */
    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    /** Состояния участия; набор закрыт ограничением базы. */
    public enum MemberStatus {
        /** Позвали, ответа нет. */
        INVITED("invited"),
        /** В звонке (или вправе в него войти). */
        MEMBER("member"),
        /** Отклонил приглашение. */
        DECLINED("declined"),
        /** Вышел сам. */
        LEFT("left"),
        /** Выгнал хозяин. */
        REMOVED("removed");

        private final String wireValue;

        MemberStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        public static MemberStatus fromWire(String value) {
            for (MemberStatus status : values()) {
                if (status.wireValue.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Неизвестное состояние участия: " + value);
        }
    }

    /** Видео-чат кончился: закрыт либо истёк. */
    public static boolean expired(boolean closed, long expiresAtMs, long nowMs) {
        return closed || (expiresAtMs > 0 && expiresAtMs <= nowMs);
    }

    /**
     * Есть ли место ещё одному.
     *
     * <p>Приглашённые считаются вместе с вошедшими: приглашение — это
     * обещание места, и звать семнадцатого, когда шестнадцать уже обещаны,
     * значит обещать то, чего нет.
     */
    public static boolean hasRoomFor(int members, int invited) {
        return members + invited < MAX_PARTICIPANTS;
    }

    /**
     * Вместимость игровой комнаты под состав звонка: ближайший чётный стол.
     *
     * <p>Комната на четверых для двоих и комната на десятерых для девяти —
     * то же правило, по которому экран главной предлагает размер стола.
     */
    public static int gameRoomCapacityFor(int players) {
        if (players <= 4) {
            return 4;
        }
        if (players <= 6) {
            return 6;
        }
        if (players <= 8) {
            return 8;
        }
        return MAX_GAME_PLAYERS;
    }
}
