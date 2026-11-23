package ru.hothat.testbot.api;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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

    // Store ONLY the SHA-256 of the private Immers token on Railway.
    // An absent digest disables machine routes.
    @Value("${HOT_HAT_GPU_TOKEN_SHA256:}")
    private String gpuTokenHash;

    private void authenticate(String provided) {
        if(gpuTokenHash==null || !gpuTokenHash.matches("[a-f0-9]{64}"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NEURO_GPU_MACHINE_AUTH_UNCONFIGURED");
        if(provided==null || provided.length()<32 || provided.length()>256)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "NEURO_GPU_MACHINE_UNAUTHORIZED");
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256")
                    .digest(provided.getBytes(StandardCharsets.UTF_8));
            byte[] expected=HexFormat.of().parseHex(gpuTokenHash);
            if(!MessageDigest.isEqual(expected,digest))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "NEURO_GPU_MACHINE_UNAUTHORIZED");
        }catch(java.security.NoSuchAlgorithmException missing){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NEURO_GPU_DIGEST_UNAVAILABLE");
        }
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
