package ru.hothat.team.store;

import java.util.List;
import java.util.Optional;

/**
 * Хранилище команд, отвечающее на единственный вопрос правила доступа —
 * «в какой я команде».
 *
 * <p>Лежит в том же пакете, что и настоящее: репозитории, которые тот просит
 * конструктором, за пределами пакета не видны. Наследование, а не подделка
 * интерфейса: {@code TeamStore} — класс, и подменять его на что-то другое
 * значило бы проверять права поверх выдуманного хранилища.
 */
public final class FakeTeamStore extends TeamStore {

    private TeamStore.TeamRow team;

    private FakeTeamStore() {
        super(null, null, null, null, null, null, null);
    }

    /** Игрок без команды. */
    public static FakeTeamStore empty() {
        return new FakeTeamStore();
    }

    public static FakeTeamStore of(String teamId, String status, String division, String... memberUids) {
        FakeTeamStore store = new FakeTeamStore();
        List<TeamStore.MemberRow> members = List.of(memberUids).stream()
                .map(uid -> new TeamStore.MemberRow(uid, TeamStore.CAPTAIN, TeamStore.ACCEPTED))
                .toList();
        store.team = new TeamStore.TeamRow(teamId, "Пара", division, status, 0L,
                memberUids.length == 0 ? null : memberUids[0], members);
        return store;
    }

    @Override
    public Optional<TeamRow> teamOf(String uid) {
        return Optional.ofNullable(team);
    }
}
