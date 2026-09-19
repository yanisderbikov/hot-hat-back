package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Блокировки в таблице {@code v2.user_ban}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link ModerationStore}.
 *
 * <p>Таблица — история, а не флаг: снятие ставит {@code lifted_at}, а не
 * удаляет строку. Поэтому «сколько раз этого человека блокировали» наконец
 * имеет ответ, а действующая блокировка — это строка без даты снятия, одна
 * на игрока по частичному уникальному индексу {@code ux_user_ban_active}.
 */
@Repository
interface PlayerBans extends JpaRepository<PlayerBan, Long> {

    Optional<PlayerBan> findFirstByPlayerIdAndLiftedAtIsNull(UUID playerId);

    /** Действующие блокировки названных игроков разом: дашборд — один запрос. */
    List<PlayerBan> findByPlayerIdInAndLiftedAtIsNull(Collection<UUID> playerIds);
}
