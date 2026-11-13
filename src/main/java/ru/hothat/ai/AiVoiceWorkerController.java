package ru.hothat.ai;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.hothat.config.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Machine-only endpoint. Never give the worker secret to browsers.
 * PermitAll in WebSecurityConfig applies ONLY to this path, with a separate
 * constant-time server secret check before any game or paid usage operation.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/machine/gpt-live/{sessionId}")
public class AiVoiceWorkerController {
    private final AiVoiceGameService game;
    private final AiVoiceUsageService usage;

    @Value("${hot-hat.gpt-live.worker-secret:}")
    private String workerSecret;

    private void authenticate(String supplied) {
        if(workerSecret==null || workerSecret.length()<32 || supplied==null ||
                !MessageDigest.isEqual(
                    workerSecret.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.of("AI_WORKER_UNAUTHORIZED",403);
        }
    }

    public record SpeechRequest(String speakerUid,String transcript,String actionId) {}
    public record UsageReport(double voiceSeconds,long backendInputTokens,
                              long backendOutputTokens,boolean finished,String errorCode) {}

    @GetMapping("/context")
    public ResponseEntity<Map<String,Object>> context(@PathVariable String sessionId,
            @RequestHeader(value="X-HH-AI-WORKER-KEY",required=false) String secret) {
        authenticate(secret);
        return ResponseEntity.ok(game.context(sessionId));
    }

    @PostMapping("/choose-explainer")
    public ResponseEntity<Map<String,Object>> choose(@PathVariable String sessionId,
            @RequestHeader(value="X-HH-AI-WORKER-KEY",required=false) String secret) {
        authenticate(secret);
        return ResponseEntity.ok(game.aiChoosesToExplain(sessionId));
    }

    @PostMapping("/heard")
    public ResponseEntity<Map<String,Object>> heard(@PathVariable String sessionId,
            @RequestHeader(value="X-HH-AI-WORKER-KEY",required=false) String secret,
            @RequestBody SpeechRequest body) {
        authenticate(secret);
        return ResponseEntity.ok(game.observedSpeech(sessionId,body.speakerUid(),
                body.transcript(),body.actionId()));
    }

    @PostMapping("/skip")
    public ResponseEntity<Map<String,Object>> skip(@PathVariable String sessionId,
            @RequestHeader(value="X-HH-AI-WORKER-KEY",required=false) String secret,
            @RequestBody SpeechRequest body) {
        authenticate(secret);
        return ResponseEntity.ok(game.skipOnHumanRequest(sessionId,body.speakerUid(),
                body.transcript(),body.actionId()));
    }

    @PostMapping("/usage")
    public ResponseEntity<Map<String,Object>> report(@PathVariable String sessionId,
            @RequestHeader(value="X-HH-AI-WORKER-KEY",required=false) String secret,
            @RequestBody UsageReport body) {
        authenticate(secret);
        return ResponseEntity.ok(usage.report(sessionId,body.voiceSeconds(),
                body.backendInputTokens(),body.backendOutputTokens(),
                body.finished(),body.errorCode()));
    }
}
