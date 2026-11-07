package ru.hothat.testbot.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.testbot.usecase.NeuroReplacementPilotService;

// These time-limited opaque URLs must remain fetchable by GenAPI without a Bearer token.
@SecurityRequirements
@RestController
@RequiredArgsConstructor
public class NeuroPublicReferenceController {
    private final NeuroReplacementPilotService pilot;

    @GetMapping(value="/api/v2/public/neuro-references/{token}/input.mp4",produces="video/mp4")
    @ApiResponse(responseCode = "200", description = "Временный публичный MP4-видеореференс без Bearer для GenAPI",
            content = @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> reference(@Parameter(description = "Непредсказуемый ограниченный по времени токен уже сохранённого референса") @PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(pilot.publicKlingReference(token));
    }
    @GetMapping(value="/api/v2/public/neuro-references/{token}/input.jpg",produces="image/jpeg")
    @ApiResponse(responseCode = "200", description = "Временный публичный JPEG-кадр для GenAPI",
            content = @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> still(@Parameter(description = "Непредсказуемый ограниченный по времени токен уже сохранённого референса") @PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("image/jpeg"))
                .body(pilot.publicKlingStill(token));
    }

    @GetMapping(value="/api/v2/public/neuro-references/{token}/input.wav",produces="audio/wav")
    @ApiResponse(responseCode = "200", description = "Временный публичный WAV-референс голоса для GenAPI",
            content = @Content(mediaType = "audio/wav", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> voice(@Parameter(description = "Непредсказуемый ограниченный по времени токен уже сохранённого референса") @PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(pilot.publicVoiceReference(token));
    }

}
