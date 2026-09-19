package ru.hothat.recording.domain;

import java.util.Map;

/**
 * Стадия задания Egress — единственный статус записи наружу.
 *
 * <p>Раньше статусов было два и они спорили: строковый {@code status} рядом с
 * числовым {@code livekit_status}. Клиент читал то один, то другой, и
 * «завершена» у них расходилось. Здесь сырой код LiveKit только ВХОДИТ —
 * дальше него не идёт и хранится лишь в журнале вебхуков.
 *
 * <p>Чистые правила: ни Spring, ни базы, ни часов.
 */
public enum EgressStage {

    STARTING("starting"),
    ACTIVE("active"),
    PROCESSING("processing"),
    COMPLETE("complete"),
    FAILED("failed");

    /** Коды состояний LiveKit Egress: 0 STARTING … 6 LIMIT_REACHED. */
    private static final Map<String, Integer> LIVEKIT_CODES = Map.ofEntries(
            Map.entry("EGRESS_STARTING", 0), Map.entry("STARTING", 0),
            Map.entry("EGRESS_ACTIVE", 1), Map.entry("ACTIVE", 1),
            Map.entry("EGRESS_ENDING", 2), Map.entry("ENDING", 2),
            Map.entry("EGRESS_COMPLETE", 3), Map.entry("COMPLETE", 3),
            Map.entry("EGRESS_FAILED", 4), Map.entry("FAILED", 4),
            Map.entry("EGRESS_ABORTED", 5), Map.entry("ABORTED", 5),
            Map.entry("EGRESS_LIMIT_REACHED", 6), Map.entry("LIMIT_REACHED", 6));

    /** Стадия «файл удалён» стадией задания не является: её даёт артефакт. */
    public static final String DELETED = "deleted";

    private final String wire;

    EgressStage(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /** Задание кончилось: ни вебхук, ни опрос больше ничего не изменят. */
    public boolean finished() {
        return this == COMPLETE || this == FAILED;
    }

    /** Задание живо: его имеет смысл останавливать и опрашивать. */
    public boolean running() {
        return this == STARTING || this == ACTIVE || this == PROCESSING;
    }

    public static EgressStage ofWire(String value) {
        for (EgressStage stage : values()) {
            if (stage.wire.equals(value)) {
                return stage;
            }
        }
        // Незнакомая строка — это «идёт обработка»: считать её провалом нельзя,
        // а считать завершением тем более.
        return PROCESSING;
    }

    /**
     * Перевод кода LiveKit в нашу стадию.
     *
     * @param code код состояния или отрицательное число, если его не прислали
     * @param fallback чем остаться, когда кода нет: молча съезжать в STARTING
     *                 нельзя — вебхук без статуса откатил бы уже активную запись
     */
    public static EgressStage fromLiveKit(int code, EgressStage fallback) {
        return switch (code) {
            case 0 -> STARTING;
            case 1 -> ACTIVE;
            case 2 -> PROCESSING;
            case 3 -> COMPLETE;
            default -> code >= 4 ? FAILED : fallback;
        };
    }

    /** Код LiveKit из ответа: число, строка-имя или строка-число. */
    public static int liveKitCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String key = value == null ? "" : value.toString().trim().toUpperCase();
        if (key.isEmpty()) {
            return -1;
        }
        Integer mapped = LIVEKIT_CODES.get(key);
        if (mapped != null) {
            return mapped;
        }
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
