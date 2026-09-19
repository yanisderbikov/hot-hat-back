package ru.hothat.team.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Дверь в {@code v2.team_preflight_session}; наружу области не выходит. */
@Repository
interface TeamPreflightSessions extends JpaRepository<TeamPreflightSession, UUID> {
}
