package ru.hothat.game.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.usecase.EphemeralMemeClipStore;

import java.util.Map;

/** SCRUM-58: explicit, short-lived, user-supplied MP4 instead of scraping YouTube. */
@RestController
@RequestMapping("/api/v2/game/{roomId}/ephemeral-memes")
@PreAuthorize("@gameAuthz.isPlayer(#roomId)")
public class EphemeralMemeClipController {
    private final EphemeralMemeClipStore clips;

    public EphemeralMemeClipController(EphemeralMemeClipStore clips) {
        this.clips = clips;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable @RoomId String roomId,
            @AuthenticationPrincipal HotHatUser user,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "startSeconds", defaultValue = "0") double startSeconds,
            @RequestParam(value = "title", defaultValue = "") String title) {
        var clip = clips.prepare(roomId, user.uid(), file, startSeconds, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "memeId", clip.id(),
                "durationMs", clip.durationMs(),
                "title", clip.title(),
                "expiresAtMs", clip.expiresAtMs()));
    }

    @GetMapping(value = "/{clipId}", produces = "video/mp4")
    public ResponseEntity<byte[]> play(@PathVariable @RoomId String roomId,
                                      @PathVariable String clipId) {
        var clip = clips.find(roomId, clipId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .contentLength(clip.data().length)
                .body(clip.data());
    }
}
