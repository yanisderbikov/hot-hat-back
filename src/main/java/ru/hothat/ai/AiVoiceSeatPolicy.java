package ru.hothat.ai;

import ru.hothat.config.ApiException;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;

import java.util.List;
import java.util.Objects;

/**
 * Authoritative room admission policy for a GPT-Live participant.
 * A bot can only complete a pair with exactly one REAL person.
 * Unranked classic and sabotage rooms (including test-bot rooms) use
 * identical rules. Existing scripted bots are not AI partner candidates.
 */
public final class AiVoiceSeatPolicy {
    public static final String PREFIX = "ai-gpt-live-";

    private AiVoiceSeatPolicy() {}

    public static boolean isAi(String uid) {
        return uid != null && uid.startsWith(PREFIX);
    }

    public static boolean isHuman(RoomPlayer player) {
        return player != null && !Boolean.TRUE.equals(player.getIsTestBot())
                && !isAi(player.getUid());
    }

    public static void requireEligible(Room room, String teamId, List<RoomPlayer> players) {
        if (Boolean.TRUE.equals(room.getRanked())) {
            throw ApiException.of("AI_RANKED_FORBIDDEN", 403);
        }
        if (!"setup".equals(room.getPhase())) {
            throw ApiException.of("AI_SETUP_ONLY", 409);
        }
        if (teamId == null || teamId.isBlank()) {
            throw ApiException.of("AI_TEAM_REQUIRED", 400);
        }
        List<RoomPlayer> members = players.stream()
                .filter(p -> Objects.equals(teamId, p.getTeamId()))
                .toList();
        if (members.stream().anyMatch(p -> isAi(p.getUid()))) {
            throw ApiException.of("AI_ALREADY_IN_TEAM", 409);
        }
        long humans = members.stream().filter(AiVoiceSeatPolicy::isHuman).count();
        if (humans != 1L) {
            throw ApiException.of("AI_NEEDS_EXACTLY_ONE_HUMAN", 409);
        }
        // A team can contain a real human and ONE scripted test bot before
        // replacement, but no other second human or multiple bot occupants.
        if (members.size() > 2 || (members.size() == 2
                && !Boolean.TRUE.equals(room.getIsTestRoom()))) {
            throw ApiException.of("AI_TEAM_FULL", 409);
        }
        if (members.size() == 2 && !members.get(0).getIsTestBot()
                && !members.get(1).getIsTestBot()) {
            throw ApiException.of("AI_TEAM_FULL", 409);
        }
    }
}
