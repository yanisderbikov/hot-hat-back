package ru.hothat.testbot.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.testbot.usecase.NeuroReplacementPilotService;

@RestController
@RequiredArgsConstructor
public class NeuroPublicReferenceController {
    private final NeuroReplacementPilotService pilot;

    @GetMapping(value="/api/v2/public/neuro-references/{token}/input.mp4",produces="video/mp4")
    public ResponseEntity<byte[]> reference(@PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(pilot.publicKlingReference(token));
    }
    @GetMapping(value="/api/v2/public/neuro-references/{token}/input.jpg",produces="image/jpeg")
    public ResponseEntity<byte[]> still(@PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("image/jpeg"))
                .body(pilot.publicKlingStill(token));
    }

    @GetMapping(value="/api/v2/public/neuro-references/{token}/input.wav",produces="audio/wav")
    public ResponseEntity<byte[]> voice(@PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(pilot.publicVoiceReference(token));
    }

}
