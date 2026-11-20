package ru.hothat.game.domain.weapon;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short, ephemeral YouTube meme selection. Never writes a meme to the catalog:
 * the self-describing ID travels as part of a normal authorized sabotage event.
 *
 * <p>Format: yt:{11-char-video-id}:{start-ms}:{length-ms}. The server builds
 * every URL itself and accepts only a constrained ASCII YouTube identifier.
 * The IFrame API cannot read or remove controls/branding from all videos.
 */
public final class YoutubeMemeClip {

    private static final Pattern ID =
            Pattern.compile("^yt:([A-Za-z0-9_-]{11}):(0|[1-9][0-9]{0,7}):(0|[1-9][0-9]{0,5})$");

    private YoutubeMemeClip() {
    }

    public static boolean isYoutubeId(String memeId) {
        return memeId != null && memeId.startsWith("yt:");
    }

    public static Optional<MemeMedia> parse(String memeId) {
        if (memeId == null) return Optional.empty();
        Matcher matcher = ID.matcher(memeId);
        if (!matcher.matches()) return Optional.empty();

        try {
            String videoId = matcher.group(1);
            long startMs = Long.parseLong(matcher.group(2));
            long lengthMs = Long.parseLong(matcher.group(3));
            if (startMs > 86_400_000L || lengthMs < 400 || lengthMs > 10_000)
                return Optional.empty();
            long start = startMs / 1000;
            long end = Math.max(start + 1, (startMs + lengthMs + 999) / 1000);
            String src = "https://www.youtube-nocookie.com/embed/" + videoId
                    + "?autoplay=1&playsinline=1&controls=0&enablejsapi=1&start="
                    + start + "&end=" + end;
            return Optional.of(new MemeMedia(
                    memeId, "Видео YouTube", lengthMs, src,
                    "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg",
                    "", "", "youtube-stream"));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
