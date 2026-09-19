package ru.hothat.auth.store;

import org.springframework.data.domain.Limit;
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
 * Учётки в таблице {@code v2.user_account}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link IdentityStore}. Сущность
 * {@code UserAccount} в сигнатуры сценариев и в DTO не попадает, поэтому хеш
 * пароля не может уехать наружу даже случайной сериализацией.
 *
 * <p>Три операции объявлены запросами, а не чтением с последующим сохранением,
 * и это главное, ради чего репозиторий вообще написан руками. Поколение
 * токенов поднимается одним {@code UPDATE … + 1}: сегодня это
 * read-modify-write в двух местах сразу, и одновременные бан и смена пароля
 * теряют один инкремент — отозванная сессия остаётся живой. Апгрейд гостя —
 * условный {@code UPDATE … WHERE kind = 'guest'}: без условия два
 * одновременных апгрейда проходили оба (дыра A4).
 */
@Repository
interface UserAccounts extends JpaRepository<UserAccount, UUID> {

    /** Вход ищет учётку по почте без учёта регистра — под это стоит ux_user_account_email. */
    Optional<UserAccount> findFirstByEmailIgnoreCase(String email);

    /** Пачкой: список игроков дашборда — один запрос, а не запрос на человека. */
    List<UserAccount> findByPlayerIdIn(Collection<UUID> playerIds);

    /** Реестр владельца: только полноценные учётки, новые сверху. */
    List<UserAccount> findByKindOrderByMemberSinceDesc(String kind, Limit limit);

    long countByKind(String kind);

    /** Сколько человек зарегистрировалось за период; считает база, а не память. */
    long countByKindAndMemberSinceBetween(String kind, Instant from, Instant to);

    /**
     * Поднять поколение токенов атомарно.
     *
     * @return число изменённых строк; 0 — учётки нет
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserAccount a set a.tokenVersion = a.tokenVersion + 1 where a.playerId = :playerId")
    int bumpTokenVersion(@Param("playerId") UUID playerId);

    /**
     * Превратить гостя в участника. Условие по роду — не украшение: оно и есть
     * защита от двух одновременных апгрейдов одной гостевой учётки.
     *
     * <p>Поколение токенов здесь не трогается: гасить прежние сессии — решение
     * сценария, а не хранилища, и он гасит их отдельным вызовом, чтобы бан,
     * смена пароля и апгрейд делали это одним и тем же способом.
     *
     * @return 1 — превратили; 0 — учётки нет либо она уже не гостевая
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserAccount a
               set a.kind = 'member',
                   a.email = :email,
                   a.passwordHash = :passwordHash,
                   a.memberSince = :now,
                   a.passwordUpdatedAt = :now
             where a.playerId = :playerId and a.kind = 'guest'
            """)
    int upgradeGuest(@Param("playerId") UUID playerId,
                     @Param("email") String email,
                     @Param("passwordHash") String passwordHash,
                     @Param("now") Instant now);

    /**
     * Задать новый пароль и тем же оператором отозвать выданные токены:
     * смена пароля обязана выкидывать чужие сессии, а не только свою.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserAccount a
               set a.passwordHash = :passwordHash,
                   a.passwordUpdatedAt = :now,
                   a.tokenVersion = a.tokenVersion + 1
             where a.playerId = :playerId
            """)
    int replacePassword(@Param("playerId") UUID playerId,
                        @Param("passwordHash") String passwordHash,
                        @Param("now") Instant now);
}
