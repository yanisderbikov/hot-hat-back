package ru.hothat.media.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.usecase.PrepareYoutubeMemeClipUseCase;

/**
 * A no-install fallback for user-provided public YouTube videos that YouTube
 * refuses to embed: fetch a short MP4, then play it inside HOT-HAT.
 * No arbitrary remote URLs, no unauthenticated bulk downloading, no browser
 * extension, and no opening the third-party page in the user's browser.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/media/youtube")
@Tag(name = "Media · YouTube clips", description = "Подготовка видеофрагмента для мемов")
@SecurityRequirement(name = "Bearer")
public class YoutubeClipController {
    private final PrepareYoutubeMemeClipUseCase prepare;

    public record ClipRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{11}") String videoId,
            double startSeconds
    ) { }

    @Operation(summary = "Подготовить 10-секундный MP4 из разрешённого источника YouTube",
            description = "Только публичные доступные фрагменты без DRM. Ограничения: 24 МБ, до 2 одновременно.")
    @PostMapping(value = "/preview-clips", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "video/mp4")
    public ResponseEntity<byte[]> preview(@AuthenticationPrincipal HotHatUser user,
                                          @Valid @RequestBody ClipRequest request) {
        if (user == null) throw ApiException.of("AUTH_REQUIRED", 401);
        byte[] clip = prepare.run(request.videoId(), request.startSeconds());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .contentLength(clip.length)
                .cacheControl(CacheControl.noStore())
                .body(clip);
    }
}
