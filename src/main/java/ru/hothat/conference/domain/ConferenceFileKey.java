package ru.hothat.conference.domain;

import java.util.regex.Pattern;

/**
 * Ключ вложения в хранилище: {@code conference/<видео-чат>/<игрок>/<время>-<hex>-<имя>}.
 *
 * <p>Ключ строит сервер при выдаче билета, и по нему же читается владение:
 * сообщение с файлом принимается, только если ключ лежит в папке этого
 * видео-чата и этого отправителя. Клиент не может ни выбрать чужую папку,
 * ни угадать её — в ключе случайный хвост.
 */
public final class ConferenceFileKey {

    private ConferenceFileKey() {
    }

    private static final String PREFIX = "conference/";

    /** Форма ключа целиком; проверяется до любого обращения к хранилищу. */
    private static final Pattern SHAPE = Pattern.compile(
            "^conference/vc-[a-f0-9]{16}/[A-Za-z0-9_-]{1,128}/[0-9]{1,20}-[a-f0-9]{12}-[^/\\\\]{1,160}$");

    public static String of(String conferenceId, String uid, long stampMs, String hex, String fileName) {
        return PREFIX + conferenceId + "/" + safeUid(uid) + "/" + stampMs + "-" + hex + "-" + safeName(fileName);
    }

    /** Лежит ли ключ в папке этого видео-чата и этого игрока. */
    public static boolean belongsTo(String key, String conferenceId, String uid) {
        if (key == null || !SHAPE.matcher(key).matches()) {
            return false;
        }
        return key.startsWith(PREFIX + conferenceId + "/" + safeUid(uid) + "/");
    }

    /**
     * Имя файла без знаков, которые хранилище или браузер поймут как путь.
     * Пустое имя становится {@code file}: без имени вложение нечем подписать.
     */
    public static String safeName(String value) {
        String name = (value == null ? "" : value)
                // Управляющие знаки и переводы строк — пробелы, разделители
                // пути и запрещённые в именах файлов знаки — подчёркивания.
                .replaceAll("[\\x00-\\x1f\\x7f]+", " ")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (name.length() > 160) {
            name = name.substring(0, 160);
        }
        return name.isEmpty() ? "file" : name;
    }

    /** В имени папки допустимы только безопасные знаки идентификатора. */
    private static String safeUid(String uid) {
        String safe = (uid == null ? "" : uid).replaceAll("[^A-Za-z0-9_-]", "_");
        safe = safe.length() > 128 ? safe.substring(0, 128) : safe;
        return safe.isEmpty() ? "user" : safe;
    }
}
