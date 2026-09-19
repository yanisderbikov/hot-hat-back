package ru.hothat.admin.port;

import java.util.Optional;

/**
 * Архив записей глазами консоли администратора.
 *
 * <p>Права здесь уже не проверяются — их проверил сценарий. Отличие
 * администратора от игрока в том, что ссылку ему дают на ЛЮБУЮ запись: он
 * разбирает жалобу, а не смотрит своё. Пока это был один метод с булевым
 * аргументом, оба правила жили в одном {@code if} и менялись вместе.
 *
 * <p>Сущность записи наружу не выходит: у неё сотня полей, из которых консоли
 * нужны четыре, а право менять остальные девяносто шесть ей ни к чему.
 */
public interface RecordingArchivePort {

    /** Запись по её идентификатору; пусто — такой записи нет. */
    Optional<Archived> find(String recordingId);

    /** Подписать ссылки на просмотр и скачивание. Ходит в хранилище по сети. */
    PlaybackUrls signUrls(Archived recording, String downloadName);

    /**
     * Снять запись из обращения.
     *
     * <p>Строка остаётся и получает состояние {@code deleted}, а исчезает
     * файл: на запись ссылаются сообщения переписки, и снос строки оставил бы
     * в чужом чате карточку, ведущую в никуда.
     */
    void withdraw(Archived recording);

    /** Запись глазами консоли: столько, сколько нужно для ссылки и сноса. */
    record Archived(String recordingId, String roomId, int gameNumber, String objectPath) {
    }

    /** Подписанные ссылки и срок их жизни. */
    record PlaybackUrls(String watchUrl, String downloadUrl, long expiresAtMs) {
    }
}
