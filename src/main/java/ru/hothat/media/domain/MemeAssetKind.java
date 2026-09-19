package ru.hothat.media.domain;

import java.util.Map;

/**
 * Что за файл мема: ролик или картинка-заставка.
 *
 * <p>Сегодня это строка {@code kind} в теле одного запроса, по которой
 * {@code MediaServiceImpl:115-123} выбирает путь, список MIME, предел размера
 * и код ошибки. В v2 вид файла назван адресом, а здесь остаётся то, что от
 * него действительно зависит: имя объекта и допустимые типы содержимого.
 */
public enum MemeAssetKind {

    /** Сам ролик: его проигрывает диверсия. */
    VIDEO("video", Map.of(
            "webm", "video/webm",
            "mp4", "video/mp4",
            "ogv", "video/ogg")),

    /** Заставка: первый кадр, чтобы карточка не мигала чёрным до загрузки. */
    POSTER("poster", Map.of(
            "webp", "image/webp",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg"));

    private final String fileStem;
    private final Map<String, String> mimeByExtension;

    MemeAssetKind(String fileStem, Map<String, String> mimeByExtension) {
        this.fileStem = fileStem;
        this.mimeByExtension = mimeByExtension;
    }

    /** Имя объекта без расширения: {@code memes/…/{memeId}/video.webm}. */
    public String fileStem() {
        return fileStem;
    }

    /** Тип содержимого по расширению; {@code null} — расширение не наше. */
    public String mimeOfExtension(String extension) {
        return extension == null ? null : mimeByExtension.get(extension.toLowerCase());
    }

    /**
     * Расширение объекта по типу содержимого; {@code null} — тип не наш.
     *
     * <p>Нужно выдаче билета: имя объекта строит сервер, а тип называет
     * клиент, и связь между ними обязана быть одной на обе стороны. Раньше
     * та же пара таблиц лежала в переходном движке, и добавить формат значило
     * бы не забыть про оба места.
     */
    public String extensionOfMime(String mime) {
        if (mime == null) {
            return null;
        }
        String clean = mime.split(";")[0].trim().toLowerCase();
        for (Map.Entry<String, String> entry : mimeByExtension.entrySet()) {
            if (entry.getValue().equals(clean)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
