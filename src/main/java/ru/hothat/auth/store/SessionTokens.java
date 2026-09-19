package ru.hothat.auth.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh-токены в таблице {@code v2.refresh_token}.
 *
 * <p>Поиск идёт через {@code findByTokenHash}, а не через {@code findById}:
 * ключ здесь — массив байт, и {@code findById} сравнивал бы его ссылочным
 * {@code equals}. Разница видна не на компиляции, а на входе, поэтому метод
 * назван явно.
 *
 * <p>Гашение цепочки и всех сессий владельца — одиночные {@code UPDATE}, а не
 * чтение списка и сохранение по одному: у активного игрока живых токенов
 * столько, сколько у него устройств и вкладок.
 */
@Repository
interface SessionTokens extends JpaRepository<SessionToken, byte[]> {

    Optional<SessionToken> findByTokenHash(byte[] tokenHash);

    /**
     * Погасить всё живое у игрока. Причина ставится вместе с датой: этого
     * требует ограничение базы, и по ней потом видно, чем закончилась сессия.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SessionToken t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.playerId = :playerId and t.revokedAt is null
            """)
    int revokeAllOfPlayer(@Param("playerId") UUID playerId,
                          @Param("now") Instant now,
                          @Param("reason") String reason);

    /**
     * Погасить одну цепочку ротаций — ответ на повторное предъявление
     * погашенного токена (аудит A11). Без неё отзывался ровно предъявленный
     * токен, а вор продолжал крутить свою ветку.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SessionToken t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("now") Instant now,
                     @Param("reason") String reason);

    /** Плановая уборка: истёкшее не нужно ни для входа, ни для разбора кражи. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SessionToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
