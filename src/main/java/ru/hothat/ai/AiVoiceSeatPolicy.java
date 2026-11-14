package ru.hothat.ai;

import ru.hothat.config.ApiException;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;

import java.util.List;
import java.util.Objects;

/** Admission is enforced by the server; the browser's owner controls are advisory. */
public final class AiVoiceSeatPolicy {
    public static final String PREFIX = "ai-gpt-live-";
    public static final int MAX_AI_PER_ROOM = 5;

    private AiVoiceSeatPolicy() {}

    public static boolean isAi(String uid) {
        return uid != null && uid.startsWith(PREFIX);
    }

    public static boolean isHuman(RoomPlayer player) {
        return player != null && !Boolean.TRUE.equals(player.getIsTestBot())
                && !isAi(player.getUid());
    }

    /** Legacy entry point: nobody can add two AIs to the same team implicitly. */
    public static void requireEligible(Room room, String teamId, List<RoomPlayer> players) {
        requireEligible(room, teamId, players, false);
    }

    /** allowOwnerDuo is derived solely from authenticated ROLE_OWNER. */
    public static void requireEligible(Room room, String teamId, List<RoomPlayer> players,
                                       boolean allowOwnerDuo) {
        if (Boolean.TRUE.equals(room.getRanked())) throw ApiException.of("AI_RANKED_FORBIDDEN", 403);
        if (!"setup".equals(room.getPhase())) throw ApiException.of("AI_SETUP_ONLY", 409);
        if (teamId == null || teamId.isBlank()) throw ApiException.of("AI_TEAM_REQUIRED", 400);
        if (players.stream().filter(p -> isAi(p.getUid())).count() >= MAX_AI_PER_ROOM)
            throw ApiException.of("AI_ROOM_MAX_FIVE", 409);

        List<RoomPlayer> members = players.stream()
                .filter(p -> Objects.equals(teamId, p.getTeamId())).toList();
        long humans = members.stream().filter(AiVoiceSeatPolicy::isHuman).count();
        long ai = members.stream().filter(p -> isAi(p.getUid())).count();
        long scripted = members.stream().filter(p -> Boolean.TRUE.equals(p.getIsTestBot())
                && !isAi(p.getUid())).count();

        // A standard team has exactly one human and one AI.
        // The site's OWNER may experiment with an AI-only pair. Other room
        // hosts and ADMIN accounts cannot pass this branch.
        boolean normal = humans == 1 && ai == 0 && scripted <= 1
                && (scripted == 0 || Boolean.TRUE.equals(room.getIsTestRoom()))
                && members.size() <= 2;
        boolean ownerDuo = allowOwnerDuo && humans == 0 && scripted == 0
                && (ai == 0 || ai == 1) && members.size() <= 1;
        if (normal || ownerDuo) return;
        if (ai > 0) throw ApiException.of("AI_ALREADY_IN_TEAM",409);
        if (humans != 1) throw ApiException.of("AI_NEEDS_EXACTLY_ONE_HUMAN",409);
        throw ApiException.of("AI_TEAM_FULL",409);
    }
}
