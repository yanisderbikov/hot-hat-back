package ru.hothat.admin.domain;

/**
 * По чьей воле снят снимок расхода.
 *
 * <p>Снимают трое: расписание (четыре раза в сутки), администратор кнопкой
 * «Проверить сейчас» и агент мониторинга. Раньше это не записывалось вовсе, и
 * по истории нельзя было отличить плановый снимок от нажатия кнопки — а
 * различать надо: письма о превышении шлёт не всякий снимок.
 */
public enum SnapshotAuthor {

    SCHEDULE("schedule"),
    ADMIN("admin"),
    AGENT("agent");

    private final String wire;

    SnapshotAuthor(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /** Только ручной снимок называет человека; база проверяет это ограничением. */
    public boolean namesPerson() {
        return this == ADMIN;
    }
}
