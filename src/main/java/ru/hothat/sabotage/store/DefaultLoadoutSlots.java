package ru.hothat.sabotage.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Дверь в стартовую обойму; за пределы {@code sabotage.store} не выходит. */
@Repository
interface DefaultLoadoutSlots extends JpaRepository<DefaultLoadoutSlot, DefaultLoadoutSlotId> {

    /** Обойма по порядку слотов: этот порядок и показывает экран арсенала. */
    List<DefaultLoadoutSlot> findByPlayerIdOrderBySlotIndexAsc(UUID playerId);

    /**
     * Обоймы названных игроков разом — одним запросом на любой список.
     *
     * <p>Существует ради подбора и префлайта команды: они спрашивают обойму
     * сразу у всех участников, и до этого метода каждый стоил бы отдельного
     * обращения.
     */
    List<DefaultLoadoutSlot> findByPlayerIdInOrderByPlayerIdAscSlotIndexAsc(List<UUID> playerIds);

    /**
     * Снять обойму целиком.
     *
     * <p>Замена обоймы — это «стереть и разложить заново», а не правка слотов
     * по одному: слот меняет и значение, и номер, а уникальность
     * {@code (player, meme)} отвергла бы перестановку двух мемов местами,
     * если делать её пошагово.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DefaultLoadoutSlot s where s.playerId = :playerId")
    void clear(@Param("playerId") UUID playerId);
}
