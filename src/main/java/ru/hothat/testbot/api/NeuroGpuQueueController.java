package ru.hothat.testbot.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.usecase.NeuroReplacementPilotService;

/** Isolated RTX 4090 queue. No client-side neuro-replacement matting fallback. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/gpu/neuro")
public class NeuroGpuQueueController {
    private final NeuroReplacementPilotService service;

    // Must match the private Immers gpu-worker/.env secret. Missing token disables API.
    @Value("${HOT_HAT_GPU_TOKEN:}")
    private String gpuToken;

    private void authenticate(String provided) {
        if(gpuToken==null || gpuToken.length()<32)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NEURO_GPU_MACHINE_AUTH_UNCONFIGURED");
        if(provided==null || !MessageDigest.isEqual(
                gpuToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "NEURO_GPU_MACHINE_UNAUTHORIZED");
    }

    @GetMapping("/next")
    public Map<String,Object> next(
            @RequestHeader(name="X-Hot-Hat-Gpu-Token",required=false) String token) {
        authenticate(token);
        return service.claimServerMatte();
    }

    @GetMapping(value="/jobs/{id}/input",produces="video/mp4")
    public ResponseEntity<byte[]> input(
            @RequestHeader(name="X-Hot-Hat-Gpu-Token",required=false) String token,
            @PathVariable String id) {
        authenticate(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(service.serverMatteSource(id));
    }

    @PostMapping(value="/jobs/{id}/result",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> result(
            @RequestHeader(name="X-Hot-Hat-Gpu-Token",required=false) String token,
            @PathVariable String id,@RequestPart("video") MultipartFile video) {
        authenticate(token);
        return service.acceptServerMatte(id,video);
    }

    @GetMapping(value="/owner/{id}/alpha",produces="video/webm")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<byte[]> ownerAlpha(
            @AuthenticationPrincipal HotHatUser owner,@PathVariable String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("video/webm"))
                .body(service.ownerServerMatteVideo(owner.uid(),id));
    }

    @GetMapping(value="/owner/{id}/wan",produces="video/mp4")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<byte[]> ownerWan(
            @AuthenticationPrincipal HotHatUser owner,@PathVariable String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(service.ownerServerWanVideo(owner.uid(),id));
    }
}
