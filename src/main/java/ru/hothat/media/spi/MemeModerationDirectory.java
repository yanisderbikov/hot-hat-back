package ru.hothat.media.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.domain.MemeAssetKind;
import ru.hothat.media.store.MemeFileStorage;
import ru.hothat.media.store.MemeStore;
import ru.hothat.util.Divisions;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Реализация {@link MemeModerationPort}: живёт у владельца таблиц. */
@Component
@RequiredArgsConstructor
public class MemeModerationDirectory implements MemeModerationPort {

    /** Столько объектов бакета просматривает одна сверка. */
    private static final int SCAN_LIMIT = 2000;
    private static final String DEFAULT_VIDEO_MIME = "video/webm";
    private static final int DEFAULT_DURATION_MS = 5000;

    /** {@code data:video/webm;base64,AAAA…} — только видео и только base64. */
    private static final Pattern VIDEO_DATA_URL =
            Pattern.compile("^data:(video/[A-Za-z0-9.+-]+);base64,([A-Za-z0-9+/=]+)$");

    private static final Pattern VIDEO_OBJECT = Pattern.compile("/video\\.(webm|mp4|ogv)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSTER_OBJECT =
            Pattern.compile("/poster\\.(webp|png|jpe?g)$", Pattern.CASE_INSENSITIVE);

    private final MemeStore memes;
    private final MemeFileStorage files;
    private final Clock clock;

    @Override
    public void withdraw(String memeId) {
        if (MemeAssetKey.isBuiltin(memeId)) {
            throw ApiException.of("BUILTIN_MEME_PROTECTED", 403);
        }
        MemeStore.Card meme = memes.find(memeId)
                .orElseThrow(() -> ApiException.of("MEME_NOT_FOUND", 404));
        if (meme.builtin()) {
            throw ApiException.of("BUILTIN_MEME_PROTECTED", 403);
        }
        files.forget(memes.withdraw(memeId, Instant.now(clock)));
    }

    @Override
    public Optimized saveOptimized(String memeId, String videoDataUrl, String contentType,
                                   Integer durationMs, String version) {
        if (MemeAssetKey.isBuiltin(memeId)) {
            // Ролик встроенного мема посеян вместе со сборкой: подменять его
            // из админки значило бы разойтись с тем, что лежит рядом с фронтом.
            throw ApiException.of("BUILTIN_MEME_PROTECTED", 403);
        }
        MemeStore.Card meme = memes.find(memeId)
                .orElseThrow(() -> ApiException.of("MEME_NOT_FOUND", 404));
        Matcher matcher = VIDEO_DATA_URL.matcher(videoDataUrl == null ? "" : videoDataUrl);
        if (!matcher.matches()) {
            throw ApiException.of("VIDEO_DATA_REQUIRED");
        }
        byte[] body = Base64.getDecoder().decode(matcher.group(2));
        String mime = contentType == null || contentType.isBlank() ? matcher.group(1) : contentType;
        // Ключ прежнего ролика, а не новый: адрес мема постоянный, и менять
        // его при оптимизации значило бы разослать всем ссылку на пустоту.
        String storageKey = meme.video() != null ? meme.video().storageKey() : newVideoKey(meme, mime);
        files.put(storageKey, body, mime);

        Instant now = Instant.now(clock);
        MemeStore.Card updated = memes.recordOptimization(memeId, version, mime, body.length, durationMs, now);
        return new Optimized(memeId, body.length, mime, updated.durationMs(), version, now.toEpochMilli());
    }

    @Override
    public Reconciliation reconcile() {
        files.requireConfigured();
        List<MemeFileStorage.StoredFile> objects = files.list("memes/", SCAN_LIMIT);

        // Ролик и заставка одного мема лежат рядом — собираем их в одну запись.
        Map<String, MemeFileStorage.StoredFile> videos = new LinkedHashMap<>();
        Map<String, MemeFileStorage.StoredFile> posters = new LinkedHashMap<>();
        Map<String, String[]> paths = new LinkedHashMap<>();
        for (MemeFileStorage.StoredFile object : objects) {
            String[] parsed = parse(object.key());
            if (parsed == null) {
                continue;
            }
            if (VIDEO_OBJECT.matcher(object.key()).find()) {
                videos.put(parsed[2], object);
                paths.put(parsed[2], parsed);
            } else if (POSTER_OBJECT.matcher(object.key()).find()) {
                posters.put(parsed[2], object);
            }
        }

        int recovered = 0;
        int existing = 0;
        Instant now = Instant.now(clock);
        for (Map.Entry<String, MemeFileStorage.StoredFile> entry : videos.entrySet()) {
            String memeId = entry.getKey();
            MemeFileStorage.StoredFile video = entry.getValue();
            MemeFileStorage.StoredFile poster = posters.get(memeId);
            String[] parsed = paths.get(memeId);
            MemeStore.Recovery outcome = memes.reconcile(new MemeStore.Discovered(
                    memeId,
                    parsed[1],
                    parsed[0],
                    title(memeId),
                    DEFAULT_DURATION_MS,
                    video.key(),
                    mimeOfKey(video.key()),
                    video.size(),
                    video.lastModified(),
                    poster == null ? null : poster.key(),
                    poster == null ? null : posterMimeOfKey(poster.key()),
                    poster == null ? 0L : poster.size()), now);
            if (outcome == MemeStore.Recovery.RECOVERED) {
                recovered++;
            } else {
                existing++;
            }
        }
        return new Reconciliation(videos.size(), recovered, existing, objects.size());
    }

    private String newVideoKey(MemeStore.Card meme, String mime) {
        String extension = MemeAssetKind.VIDEO.extensionOfMime(mime);
        if (extension == null || meme.ownerUid() == null) {
            // Без владельца путь не построить: он часть ключа. Такое бывает
            // только у мема, чей ролик так и не доехал до хранилища.
            throw ApiException.of("MEME_MEDIA_MISSING", 409);
        }
        return MemeAssetKey.of(Divisions.normalize(meme.divisionLanguage()),
                meme.ownerUid(), meme.memeId(), MemeAssetKind.VIDEO, extension);
    }

    /** {@code memes/{дивизион}/{владелец}/{мем}/video.webm} — иначе объект не наш. */
    private static String[] parse(String key) {
        String[] parts = String.valueOf(key).split("/");
        if (parts.length < 5 || !"memes".equals(parts[0])) {
            return null;
        }
        if (parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank() || MemeAssetKey.isBuiltin(parts[3])) {
            return null;
        }
        return new String[]{Divisions.normalize(parts[1]), parts[2], parts[3]};
    }

    private static String title(String memeId) {
        return "Мем " + memeId.substring(Math.max(0, memeId.length() - 8));
    }

    private static String mimeOfKey(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1);
        String mime = MemeAssetKind.VIDEO.mimeOfExtension(extension);
        return mime == null ? DEFAULT_VIDEO_MIME : mime;
    }

    private static String posterMimeOfKey(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1);
        String mime = MemeAssetKind.POSTER.mimeOfExtension(extension);
        return mime == null ? "image/webp" : mime;
    }
}
