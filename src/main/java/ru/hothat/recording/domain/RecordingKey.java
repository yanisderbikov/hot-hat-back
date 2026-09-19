package ru.hothat.recording.domain;

import java.util.Optional;

/**
 * Пара «комната + номер партии» — то, чем запись называется снаружи.
 *
 * <p>В базе у записи суррогат-uuid (§6.1), но наружу она по-прежнему зовётся
 * склейкой {@code hat-…-3}: этот идентификатор лежит в личных библиотеках
 * игроков, в ссылках на записи внутри переписки и в спецификации. Менять его
 * значило бы менять форму ответов, поэтому склейка осталась — но только как
 * ИМЯ. Отбор и сортировка идут по колонкам, а не по разрезанной строке.
 *
 * <p>Разбор идёт по ПОСЛЕДНЕМУ дефису: в идентификаторе комнаты дефис тоже
 * есть ({@code hat-0f3a…}), и разбор по первому вернул бы «hat».
 */
public record RecordingKey(String roomId, int gameNumber) {

    public RecordingKey {
        roomId = roomId == null ? "" : roomId.trim();
    }

    /** Имя записи снаружи. */
    public String publicId() {
        return roomId + "-" + gameNumber;
    }

    public boolean valid() {
        // Партии нумеруются с единицы: нулевой партии не бывает, и база это
        // проверяет (ck_recording_game_number). Раньше номер зажимался в ноль
        // и заводил запись «hat-…-0», у которой не было своей партии.
        return !roomId.isEmpty() && roomId.length() <= 24 && gameNumber > 0;
    }

    /** Разбор имени; пусто — имя не похоже на запись, и искать её незачем. */
    public static Optional<RecordingKey> parse(String publicId) {
        String value = publicId == null ? "" : publicId.trim();
        int split = value.lastIndexOf('-');
        if (split <= 0 || split == value.length() - 1) {
            return Optional.empty();
        }
        try {
            RecordingKey key = new RecordingKey(value.substring(0, split),
                    Integer.parseInt(value.substring(split + 1)));
            return key.valid() ? Optional.of(key) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Путь файла в бакете. Считается, а не хранится: он полностью определён парой. */
    public String objectPath() {
        return "game-recordings/" + roomId + "/game-" + gameNumber + ".mp4";
    }

    /** Имя файла при скачивании. */
    public String downloadName() {
        return "HOT-HAT-" + (roomId.isEmpty() ? "game" : roomId) + "-" + gameNumber + ".mp4";
    }
}
