package ru.hothat.profile.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Карточки игроков в таблице {@code v2.player_profile}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link ProfileStore}.
 *
 * <p>Поиск по нику идёт по вычисляемой колонке {@code nickname_key}, которую
 * считает сама база ({@code lower(btrim(nickname))}). Поэтому «занято ли имя»
 * и «под каким ключом оно лежит» больше не могут разойтись — вместе с этим
 * исчезли таблица индекса ников, её починка и два фолбэка поиска.
 */
@Repository
interface PlayerProfiles extends JpaRepository<PlayerProfile, UUID> {

    Optional<PlayerProfile> findByNicknameKey(String nicknameKey);

    boolean existsByNicknameKey(String nicknameKey);

    /** Карточки названных игроков разом; пустой список в базу не идёт. */
    List<PlayerProfile> findByPlayerIdIn(Collection<UUID> playerIds);

    /**
     * Закрепить дивизион ровно один раз.
     *
     * <p>Условие {@code division_locked_at IS NULL} — это и есть правило
     * «выбирается однажды». Ноль изменённых строк означает, что выбор уже
     * сделан, и решение о 409 принимает вызывающий, сверив нынешнее значение.
     * Сегодня то же правило живёт в двух местах кода сразу.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PlayerProfile p
               set p.divisionLanguage = :divisionLanguage,
                   p.uiLanguage = :uiLanguage,
                   p.divisionLockedAt = :now
             where p.playerId = :playerId and p.divisionLockedAt is null
            """)
    int lockDivision(@Param("playerId") UUID playerId,
                     @Param("divisionLanguage") String divisionLanguage,
                     @Param("uiLanguage") String uiLanguage,
                     @Param("now") Instant now);
}
