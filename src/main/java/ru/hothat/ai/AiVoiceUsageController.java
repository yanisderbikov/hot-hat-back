package ru.hothat.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.hothat.config.HotHatUser;

import java.util.Map;

/**
 * Billing views belong ONLY to the product owner. Room hosts, admins,
 * spectators and ordinary team members cannot read another game's charges.
 */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v2/admin/ai-voice-usage")
@PreAuthorize("hasRole('OWNER')")
public class AiVoiceUsageController {
    private final AiVoiceUsageService usage;

    @GetMapping
    public ResponseEntity<Map<String,Object>> history(
            @AuthenticationPrincipal HotHatUser user){
        return ResponseEntity.ok(usage.ownerHistory(user));
    }

    @GetMapping("/rooms/{roomId}/games/{gameNumber}")
    public ResponseEntity<Map<String,Object>> game(
            @AuthenticationPrincipal HotHatUser user,
            @PathVariable String roomId,
            @PathVariable int gameNumber) {
        return ResponseEntity.ok(usage.ownerGame(user,roomId,gameNumber));
    }
}
