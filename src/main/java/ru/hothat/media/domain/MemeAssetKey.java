package ru.hothat.media.domain;

import ru.hothat.config.ApiException;

import java.util.regex.Pattern;

/**
 * Ключ объекта мема в хранилище и всё, что из него следует.
 *
 * <p>Путь детерминирован и строится сервером при выдаче билета:
 * {@code memes/{дивизион}/{владелец}/{мем}/video.webm}. Раз так, по ключу
 * можно ответить на три вопроса, ради которых сегодня существуют ручные
 * проверки в трёх разных местах: наш ли это объект вообще, чей он и что за
 * файл в нём лежит.
 *
 * <p>Проверка выражена одним выражением на всю форму пути, а не набором
 * запретов («не начинается с …», «не содержит {@code ..}», «не содержит
 * обратный слеш» — {@code MediaServiceImpl:90-96}). Разница принципиальная:
 * запреты надо не забыть перечислить, а форма отвергает всё, что на неё не
 * похоже, включая выход вверх по дереву — точки в сегментах не допущены вовсе.
 */
public final class MemeAssetKey {

    /** Форма ключа. Публична, потому что ей же валидируются поля DTO. */
    public static final String PATTERN =
            "^memes/[A-Za-z0-9_-]{1,16}/[A-Za-z0-9_-]{1,128}/[A-Za-z0-9_-]{1,180}/(?:video|poster)\\.[A-Za-z0-9]{2,5}$";

    private static final Pattern KEY = Pattern.compile(PATTERN);

    /** Столько знаков владельца попадает в путь — ровно как в выдаче билета. */
    private static final int OWNER_SEGMENT_LIMIT = 128;

    private MemeAssetKey() {
    }

    /**
     * Построить ключ объекта: {@code memes/{дивизион}/{владелец}/{мем}/video.webm}.
     *
     * <p>Путь строит сервер, и только он: владение потом читается прямо
     * отсюда, и на нём держатся уборка осиротевших файлов и снятие мема.
     * Собранный ключ проверяется той же формой, что и пришедший снаружи, —
     * иначе опечатка в дивизионе завела бы объект, который потом не признают
     * своим.
     */
    public static String of(String divisionLanguage, String ownerUid, String memeId,
                            MemeAssetKind kind, String extension) {
        return require("memes/" + divisionLanguage + "/" + ownerSegment(ownerUid)
                + "/" + memeId + "/" + kind.fileStem() + "." + extension);
    }

    /** Ключ нашей формы или 403: чужой формат — это попытка достать чужой объект. */
    public static String require(String value) {
        String key = value == null ? "" : value.trim();
        if (!KEY.matcher(key).matches()) {
            throw ApiException.of("MEDIA_PATH_INVALID", 403);
        }
        return key;
    }

    /**
     * Ключ, принадлежащий этому игроку. Владение читается из самого пути —
     * так же, как его туда положил сервер, выдавая билет на загрузку.
     */
    public static String requireOwnedBy(String value, String ownerUid) {
        String key = require(value);
        if (!segment(key, 2).equals(ownerSegment(ownerUid))) {
            throw ApiException.of("MEDIA_PATH_INVALID", 403);
        }
        return key;
    }

    /**
     * Ключ конкретного файла конкретного мема этого игрока.
     *
     * <p>Нужен публикации: карточка не должна ссылаться на объект из чужой
     * папки или из папки другого мема — иначе снятие мема оставит файл живым,
     * а чужая карточка вдруг начнёт показывать ваш ролик.
     */
    public static String requireAssetOf(String value, String ownerUid, String memeId, MemeAssetKind kind) {
        String key = requireOwnedBy(value, ownerUid);
        if (!segment(key, 3).equals(memeId) || !segment(key, 4).startsWith(kind.fileStem() + ".")) {
            throw ApiException.of("MEDIA_PATH_INVALID", 403);
        }
        return key;
    }

    /** Тип содержимого по расширению ключа; расширение не наше — 400. */
    public static String mimeOf(String key, MemeAssetKind kind) {
        String fileName = segment(require(key), 4);
        String mime = kind.mimeOfExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
        if (mime == null) {
            throw ApiException.of(kind == MemeAssetKind.POSTER ? "MEME_POSTER_MIME_INVALID" : "MEME_MIME_INVALID");
        }
        return mime;
    }

    /** Встроенный мем заводит посев, а не игрок: снять и переписать его нельзя. */
    public static boolean isBuiltin(String memeId) {
        return memeId != null && memeId.startsWith("builtin-");
    }

    /**
     * Владелец в пути записан не как есть: посторонние знаки заменены на
     * подчёркивание, длина обрезана. Повтор {@code MediaServiceImpl.safeUid} —
     * там метод приватный, а сверять путь с личностью надо по той же формуле,
     * иначе игрок не докажет владение собственным файлом.
     */
    public static String ownerSegment(String uid) {
        String safe = (uid == null ? "" : uid).replaceAll("[^a-zA-Z0-9_-]", "_");
        return safe.length() > OWNER_SEGMENT_LIMIT ? safe.substring(0, OWNER_SEGMENT_LIMIT) : safe;
    }

    private static String segment(String key, int index) {
        return key.split("/")[index];
    }
}
