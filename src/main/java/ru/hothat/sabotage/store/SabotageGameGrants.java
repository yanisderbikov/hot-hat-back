package ru.hothat.sabotage.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Дверь в журнал списаний; за пределы {@code sabotage.store} не выходит. */
@Repository
interface SabotageGameGrants extends JpaRepository<SabotageGameGrant, Long> {

    /**
     * Записать списание — и этим же оператором узнать, не повтор ли это.
     *
     * <p>Здесь и живёт идемпотентность квоты. Клиент шлёт списание сразу после
     * старта партии и легко повторяет его при переподключении; ключ
     * «игрок + комната + номер партии» закрыт уникальным индексом, поэтому
     * повтор возвращает ноль строк, а не вторую списанную партию. Прежний код
     * задавал тот же вопрос отдельным чтением по склеенному ключу
     * ({@code getterRanked.sabotageGameUsed}) — между чтением и записью
     * помещалась вторая вкладка.
     *
     * @return 1 — списание записано впервые; 0 — такое уже есть
     */
    @Modifying
    @Query(value = "insert into v2.sabotage_game_grant (player_id, room_id, game_number) "
            + "values (:playerId, :roomId, :gameNumber) on conflict do nothing", nativeQuery = true)
    int record(@Param("playerId") UUID playerId,
               @Param("roomId") String roomId,
               @Param("gameNumber") int gameNumber);
}
