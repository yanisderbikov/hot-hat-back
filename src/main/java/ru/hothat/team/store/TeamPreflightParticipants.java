package ru.hothat.team.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.team_preflight_participant}; наружу области не выходит.
 *
 * <p>Строки живут ровно одну проверку готовности: новая проверка их удаляет.
 * Иначе «готов» из прошлой досталось бы новой — ровно та ошибка, ради которой
 * у сессии есть поколение {@code session_seq}.
 */
@Repository
interface TeamPreflightParticipants
        extends JpaRepository<TeamPreflightParticipant, TeamPreflightParticipantId> {

    List<TeamPreflightParticipant> findByTeamId(UUID teamId);

    void deleteByTeamId(UUID teamId);
}
