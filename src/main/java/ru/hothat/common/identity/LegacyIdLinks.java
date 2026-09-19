package ru.hothat.common.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Дверь в таблицу моста; за пределы {@code common.identity} не выходит. */
@Repository
interface LegacyIdLinks extends JpaRepository<LegacyIdLink, UUID> {

    /**
     * Обратный перевод пачкой: «чьи это uuid». Один запрос на список, а не
     * запрос на каждого — иначе список друзей снова стоил бы сотню обращений.
     */
    List<LegacyIdLink> findByKindAndV2IdIn(String kind, Collection<UUID> v2Ids);

    /**
     * Запомнить пару. Вставка без предварительного чтения: значение uuid
     * посчитано из самого идентификатора, поэтому «уже есть» и «только что
     * вставили» дают одну и ту же строку, а гонка двух запросов гасится
     * первичным ключом, а не порядком проверок.
     */
    @Modifying
    @Query(value = "insert into v2.legacy_id_bridge (v2_id, kind, legacy_id) "
            + "values (:v2Id, :kind, :legacyId) on conflict do nothing", nativeQuery = true)
    void remember(@Param("v2Id") UUID v2Id, @Param("kind") String kind, @Param("legacyId") String legacyId);
}
