package ru.hothat.recording.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Срок хранения записи.
 *
 * <p>Правило одно и в одном месте: несохранённая запись живёт тридцать дней,
 * сохранённая не имеет срока вовсе. База проверяет это ограничением
 * {@code ck_recording_retention_expiry}, поэтому «истёк, но сохранена» и
 * «сохранена, но с датой удаления» в таблицу просто не попадут.
 *
 * <p>Раньше срок ставился в четырёх местах — на старте, на остановке, в
 * вебхуке и при сохранении, — и каждое считало его само.
 */
public final class RetentionRules {

    public static final Duration TTL = Duration.ofDays(30);

    /**
     * Сколько живёт подписанная ссылка на файл.
     *
     * <p>Полчаса: этого хватает досмотреть партию и скачать её, а украденная
     * ссылка протухает раньше, чем ею успеют поделиться.
     */
    public static final Duration PLAYBACK_URL_TTL = Duration.ofMinutes(30);

    private RetentionRules() {
    }

    /** Когда удалить запись, которую никто не сохранил. */
    public static Instant expiryFrom(Instant now) {
        return now.plus(TTL);
    }
}
