package ru.hothat.machine.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Сравнение секретов за постоянное время.
 *
 * <p>Сегодня cron-секрет сверяется обычным {@code equals}
 * ({@code CleanupController.cronAuthorized}), и секрет мониторинга — тоже
 * ({@code MonitorController.secretOk}). {@code String.equals} выходит на
 * первом несовпавшем символе, поэтому время ответа зависит от длины верного
 * префикса: секрет подбирается посимвольно по времени ответа сервера, а не
 * перебором целиком. {@link MessageDigest#isEqual} на массивах одинаковой
 * длины такой утечки не даёт.
 *
 * <p>Длину мы всё равно раскрываем — сравнение массивов разной длины выходит
 * сразу, — но длина секрета и так не тайна: она задана конфигурацией.
 */
public final class Secrets {

    private Secrets() {
    }

    /** Пустой ожидаемый секрет не открывает ничего: незаданный ключ — это запрет. */
    public static boolean constantTimeEquals(String expected, String presented) {
        if (expected == null || expected.isBlank() || presented == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = presented.getBytes(StandardCharsets.UTF_8);
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }
}
