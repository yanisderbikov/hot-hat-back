package ru.hothat.auth.spi;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Реализация {@link AccessTokenPort}: HMAC-SHA256 на общем секрете.
 *
 * <p>Живёт рядом с портом, как и остальные реализации spi в проекте: у ключа
 * подписи один хозяин, и вторая точка, где он читается, означала бы второй
 * секрет. Класс не публичный — снаружи видно только порт.
 *
 * <p>Секрет короче 32 байт отвергается на старте, а не молча ослабляет
 * подпись: HMAC-SHA256 принял бы и восемь байт, и узнать об этом было бы не по
 * чему.
 */
@Service
class AccessTokens implements AccessTokenPort {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    AccessTokens(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access.ttl.seconds}") long accessTtlSeconds,
            @Value("${jwt.refresh.ttl.seconds}") long refreshTtlSeconds) {
        byte[] material = secret.getBytes(StandardCharsets.UTF_8);
        if (material.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET короче 32 байт: HMAC-SHA256 не даст такой подписи стойкости");
        }
        this.key = Keys.hmacShaKeyFor(material);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @Override
    public String createAccessToken(String uid, String email, int tokenVersion, boolean guest) {
        Date now = new Date();
        return Jwts.builder()
                .subject(uid)
                .claim("email", email)
                .claim("ver", tokenVersion)
                .claim("guest", guest)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtlSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    @Override
    public String createRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    @Override
    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String getUid(String token) {
        return claims(token).getSubject();
    }

    @Override
    public String getEmail(String token) {
        return claims(token).get("email", String.class);
    }

    @Override
    public int getTokenVersion(String token) {
        Integer version = claims(token).get("ver", Integer.class);
        return version == null ? 0 : version;
    }

    @Override
    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    @Override
    public long refreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
