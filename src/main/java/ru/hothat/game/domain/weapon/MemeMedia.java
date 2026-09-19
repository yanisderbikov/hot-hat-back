package ru.hothat.game.domain.weapon;

/**
 * Мем, готовый к показу на сцене.
 *
 * <p>Домен библиотеку мемов не читает: она в чужой области и за сетью
 * (файлы лежат в S3). Всё, что нужно правилам, — длительность; остальные поля
 * едут насквозь, чтобы получатель события ничего не дочитывал.
 */
public record MemeMedia(String memeId,
                        String title,
                        long durationMs,
                        String src,
                        String poster,
                        String mediaPath,
                        String posterPath,
                        String storageProvider) {

    /** Ролик короче четверти секунды или длиннее десяти на сцене не нужен. */
    public static final long MIN_DURATION_MS = 400;
    public static final long MAX_DURATION_MS = 10_000;

    public long clampedDurationMs() {
        return Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));
    }
}
