package ru.hothat.sabotage.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Дверь в право играть с диверсиями; за пределы {@code sabotage.store} не выходит.
 *
 * <p>Две записи здесь идут одним оператором базы, а не парой «прочитать —
 * записать». Причина одна на обе: у игрока две вкладки, и обе стартуют партию.
 * Прежний код читал {@code sabotage_games_used}, прибавлял единицу в памяти и
 * сохранял карточку целиком ({@code ProfileServiceImpl.consumeSabotageGame}) —
 * второй инкремент затирал первый, и одна бесплатная партия доставалась
 * игроку дважды.
 */
@Repository
interface SabotageEntitlements extends JpaRepository<SabotageEntitlement, UUID> {

    /**
     * Завести строку права, если её ещё нет.
     *
     * <p>Вставка без предварительного чтения: «уже есть» и «только что завели»
     * дают одну и ту же строку, а гонка двух вкладок гасится первичным ключом,
     * а не порядком проверок. {@code free_games_limit} берётся из умолчания
     * колонки — предел живёт в базе, а не константой в коде.
     */
    @Modifying
    @Query(value = "insert into v2.sabotage_entitlement (player_id) values (:playerId) "
            + "on conflict do nothing", nativeQuery = true)
    void ensure(@Param("playerId") UUID playerId);

    /**
     * Списать одну бесплатную партию.
     *
     * <p>Ноль изменённых строк — это отказ: квота исчерпана. Проверка предела
     * стоит в {@code where} того же оператора, поэтому между «осталось ноль» и
     * «списываем» не помещается чужая вкладка.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update v2.sabotage_entitlement set free_games_used = free_games_used + 1 "
            + "where player_id = :playerId and not unlimited and free_games_used < free_games_limit",
            nativeQuery = true)
    int spendFreeGame(@Param("playerId") UUID playerId);

    /**
     * Выдать безлимит навсегда — так награждается дружба с владельцем сервиса.
     *
     * <p>Отдельным оператором, а не сохранением сущности: безлимит выдаётся
     * при ЧТЕНИИ квоты, и переписывать ради него всю строку значило бы гасить
     * счётчик израсходованного, прочитанный до чужого списания.
     *
     * <p>{@code clearAutomatically} здесь НЕТ, в отличие от списания, и это
     * важнее, чем кажется. Квоту спрашивают посреди чужих сценариев — подбора
     * и префлайта команды, — у которых в контексте сохранения уже лежат свои
     * изменения; очистка контекста отцепила бы их, и они молча не доехали бы
     * до базы. Устаревшего чтения при этом не возникает: выдав безлимит,
     * вызывающий возвращает безлимит, а не перечитывает строку.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "update v2.sabotage_entitlement set unlimited = true "
            + "where player_id = :playerId and not unlimited", nativeQuery = true)
    int grantUnlimited(@Param("playerId") UUID playerId);
}
