package ru.hothat.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Часы, которые двигает тест.
 *
 * <p>Движок берёт время из {@link Clock}, поэтому «ход истёк» и «отсрочка
 * последнего слова прошла» проверяются переводом стрелки, а не ожиданием
 * шестидесяти секунд.
 */
public final class TestClock extends Clock {

    private long millis;

    private TestClock(long millis) {
        this.millis = millis;
    }

    /** Круглая точка отсчёта: в ожиданиях тестов её видно глазами. */
    public static TestClock at(long epochMilli) {
        return new TestClock(epochMilli);
    }

    public static TestClock start() {
        return new TestClock(1_700_000_000_000L);
    }

    public TestClock advance(long deltaMs) {
        millis += deltaMs;
        return this;
    }

    public TestClock set(long epochMilli) {
        millis = epochMilli;
        return this;
    }

    @Override
    public long millis() {
        return millis;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
