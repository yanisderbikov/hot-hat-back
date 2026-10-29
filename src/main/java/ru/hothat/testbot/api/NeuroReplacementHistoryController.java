package ru.hothat.testbot.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.usecase.NeuroReplacementPilotService;
import java.util.List;
import java.util.Map;

/** Owner-only read-only history; unlike room endpoints it works after a game ends. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/neuro-replacements")
@PreAuthorize("hasRole('OWNER')")
@SecurityRequirement(name = "Bearer")
public class NeuroReplacementHistoryController {
    private final NeuroReplacementPilotService pilot;

    @GetMapping("/readiness")
    public Map<String,Boolean> readiness(@AuthenticationPrincipal HotHatUser owner) {
        return pilot.readiness();
    }

    @GetMapping
    public List<Map<String,Object>> history(@AuthenticationPrincipal HotHatUser owner) {
        return pilot.history(owner.uid());
    }

    @GetMapping("/{jobId}/video")
    public ResponseEntity<byte[]> video(@AuthenticationPrincipal HotHatUser owner,
                                       @PathVariable String jobId) {
        byte[] bytes = pilot.archivedVideo(owner.uid(), jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=hot-hat-neuro.mp4")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(bytes);
    }
}
