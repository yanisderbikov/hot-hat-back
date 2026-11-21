package ru.hothat.media.usecase;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Private, server-to-server MP4 source in an independent Railway region.
 * No browser YouTube iframe, user cookies, or external client URL is involved.
 * The secret is configured on Railway and is never delivered to the website.
 */
@Slf4j
final class PrivateYoutubeClipSource {
    private static final long MAX_BYTES = 24L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private PrivateYoutubeClipSource() { }

    static byte[] fetchFromEnvironment(String videoId, double start) {
        String url = System.getenv("HOT_HAT_CLIP_SOURCE_URL");
        String token = System.getenv("HOT_HAT_CLIP_SOURCE_TOKEN");
        if (url == null || url.isBlank() || token == null || token.isBlank())
            return null;
        return fetch(url, token, videoId, start);
    }

    static byte[] fetch(String url, String token, String videoId, double start) {
        PrepareYoutubeMemeClipUseCase.validate(videoId, start);
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException badUrl) {
            log.error("Private YouTube MP4 source URL is invalid");
            return null;
        }
        if (!trustedUri(uri)) {
            log.error("Private YouTube MP4 source must be HTTPS or private Railway DNS");
            return null;
        }
        String requestJson = String.format(Locale.ROOT,
                "{\"videoId\":\"%s\",\"startSeconds\":%.3f}", videoId, start);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(70))
                .header("Content-Type", "application/json")
                .header("X-Hot-Hat-Clip-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Private MP4 source refused videoId={} httpStatus={} (local fallback)",
                        videoId, response.statusCode());
                return null;
            }
            byte[] bytes = response.body();
            String type = response.headers().firstValue("Content-Type").orElse("");
            if (!type.toLowerCase(Locale.ROOT).startsWith("video/mp4")
                    || bytes.length < 2048 || bytes.length > MAX_BYTES
                    || !looksLikeMp4(bytes)) {
                log.warn("Private MP4 source sent invalid content videoId={} bytes={}",
                        videoId, bytes.length);
                return null;
            }
            log.info("Prepared clean YouTube meme through private source id={} bytes={}",
                    videoId, bytes.length);
            return bytes;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Private MP4 source interrupted for video {}", videoId);
            return null;
        } catch (IOException | IllegalArgumentException network) {
            log.warn("Private MP4 source transport unavailable for video {}: {}",
                    videoId, network.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean trustedUri(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getUserInfo() != null) return false;
        if ("https".equalsIgnoreCase(scheme)) return true;
        return "http".equalsIgnoreCase(scheme)
                && (host.endsWith(".railway.internal")
                    || "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host));
    }

    private static boolean looksLikeMp4(byte[] bytes) {
        return bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't'
                && bytes[6] == 'y' && bytes[7] == 'p';
    }
}
