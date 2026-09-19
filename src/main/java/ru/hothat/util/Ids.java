package ru.hothat.util;

import ru.hothat.config.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Форматы идентификаторов, на которые опирается фронтенд. */
public final class Ids {

    public static final Pattern ROOM = Pattern.compile("^hat-[a-f0-9]{16}$");
    public static final Pattern NICKNAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{2,19}$");
    public static final Pattern TEAM_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _-]{2,29}$");
    public static final Pattern TEST_BOT = Pattern.compile("^testbot-[a-z0-9-]{3,80}$");
    public static final Pattern MEME_ID = Pattern.compile("^(?:meme|builtin)-[a-zA-Z0-9_-]{6,100}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String hex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    public static int randomInt(int boundExclusive) {
        return RANDOM.nextInt(Math.max(1, boundExclusive));
    }

    public static int randomInt(int minInclusive, int maxExclusive) {
        return minInclusive + RANDOM.nextInt(Math.max(1, maxExclusive - minInclusive));
    }

    public static String newRoomId() {
        return "hat-" + hex(8);
    }

    public static String requireRoomId(String value) {
        String id = value == null ? "" : value.trim().toLowerCase();
        if (!ROOM.matcher(id).matches()) {
            throw ApiException.of("ROOM_INVALID", 400);
        }
        return id;
    }

    /** Комната-лобби команды детерминирована: один и тот же teamId — одна и та же комната. */
    public static String teamLobbyRoomId(String teamId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("team-lobby:" + teamId).getBytes(StandardCharsets.UTF_8));
            return "hat-" + HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String pair(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "_" + b : b + "_" + a;
    }

    public static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
