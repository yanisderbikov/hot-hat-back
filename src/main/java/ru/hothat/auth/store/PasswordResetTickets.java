package ru.hothat.auth.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Ссылки восстановления в таблице {@code v2.password_reset_token}.
 *
 * <p>Одноразовость держит условный {@code UPDATE … WHERE used_at IS NULL}:
 * два одновременных перехода по одной ссылке до него меняли пароль дважды.
 */
@Repository
interface PasswordResetTickets extends JpaRepository<PasswordResetTicket, byte[]> {

    Optional<PasswordResetTicket> findByTokenHash(byte[] tokenHash);

    /**
     * Отметить ссылку использованной. Условие — это и есть одноразовость.
     *
     * @return 1 — ссылка была живой и стала использованной; 0 — её уже применили
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetTicket t
               set t.usedAt = :now
             where t.tokenHash = :tokenHash and t.usedAt is null and t.invalidatedAt is null
            """)
    int markUsed(@Param("tokenHash") byte[] tokenHash, @Param("now") Instant now);

    /** Новая заявка обесценивает все прежние; отдельный столбец от «применили». */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetTicket t
               set t.invalidatedAt = :now
             where t.playerId = :playerId and t.usedAt is null and t.invalidatedAt is null
            """)
    int invalidateAllOfPlayer(@Param("playerId") java.util.UUID playerId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetTicket t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
