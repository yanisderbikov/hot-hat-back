package ru.hothat.auth.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Выданные права в таблице {@code v2.account_role}.
 *
 * <p>Не публичный по тем же причинам, что и {@link UserAccounts}: право —
 * это ответ на вопрос «пускать ли», и отвечать на него должно одно место.
 */
@Repository
interface AccountRoles extends JpaRepository<AccountRole, AccountRoleId> {

    List<AccountRole> findByPlayerId(UUID playerId);

    /** Права многих игроков разом: реестр учёток — один запрос на страницу. */
    List<AccountRole> findByPlayerIdIn(Collection<UUID> playerIds);

    /**
     * Кому выдано это право. Владелец у сервиса ровно один — это держит
     * частичный уникальный индекс {@code ux_account_role_single_owner}, —
     * поэтому список здесь либо пуст, либо из одной строки.
     */
    List<AccountRole> findByRole(String role);
}
