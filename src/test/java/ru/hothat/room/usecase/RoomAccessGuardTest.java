package ru.hothat.room.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.room.api.dto.RoomSeatKind;
import ru.hothat.support.FakeRoomRows;
import ru.hothat.support.FakeRooms;
import ru.hothat.support.Users;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.hothat.support.Refusals.refuses;

/**
 * Права комнаты в области {@code /api/v2}: сюда переехало всё, что было
 * закрыто в прежнем документном шлюзе. Здесь же проверяется, что своему дверь
 * открыта — иначе запрет на документы означал бы, что играть нельзя вовсе.
 */
class RoomAccessGuardTest {

    private static final String ROOM = "hat-0123456789abcdef";

    private final HotHatUser host = Users.player(Users.ANN);
    private final HotHatUser member = Users.player(Users.BOB);
    private final HotHatUser stranger = Users.player(Users.MALLORY);

    @Test
    @DisplayName("участник комнаты получает своё место")
    void memberGetsHisSeat() {
        RoomAccessGuard guard = guard(rooms().player(ROOM, Users.BOB, "Боря"), FakeRoomRows.empty());
        assertThat(guard.requireMember(member, ROOM).getUid()).isEqualTo(Users.BOB);
    }

    @Test
    @DisplayName("посторонний в комнате не участник: 403, а не 404 — комната существует")
    void strangerIsNotAMember() {
        RoomAccessGuard guard = guard(rooms().player(ROOM, Users.BOB, "Боря"), FakeRoomRows.empty());
        refuses("ROOM_MEMBER_ONLY", 403, () -> guard.requireMember(stranger, ROOM));
    }

    @Test
    @DisplayName("зритель занимает своё место, а игрок в зрительское не проходит")
    void spectatorSeatIsSeparate() {
        RoomAccessGuard guard = guard(
                rooms().player(ROOM, Users.BOB, "Боря").spectator(ROOM, Users.MALLORY, "Мэл"),
                FakeRoomRows.empty());
        assertThat(guard.requireSpectator(stranger, ROOM).getUid()).isEqualTo(Users.MALLORY);
        refuses("SPECTATOR_ONLY", 403, () -> guard.requireSpectator(member, ROOM));
    }

    @Test
    @DisplayName("место в комнате называет, кем человек сидит: игроком или зрителем")
    void seatTellsPlayerFromSpectator() {
        RoomAccessGuard guard = guard(
                rooms().player(ROOM, Users.BOB, "Боря").spectator(ROOM, Users.MALLORY, "Мэл"),
                FakeRoomRows.empty());
        assertThat(guard.requireSeat(member, ROOM))
                .isEqualTo(new RoomAccessGuard.Seat(RoomSeatKind.PLAYER, Users.BOB, "Боря"));
        assertThat(guard.requireSeat(stranger, ROOM))
                .isEqualTo(new RoomAccessGuard.Seat(RoomSeatKind.SPECTATOR, Users.MALLORY, "Мэл"));
    }

    @Test
    @DisplayName("у того, кто в комнате не сидит вовсе, места нет")
    void seatlessVisitorIsRefused() {
        RoomAccessGuard guard = guard(rooms(), FakeRoomRows.empty());
        refuses("ROOM_MEMBER_ONLY", 403, () -> guard.requireSeat(stranger, ROOM));
    }

    @Test
    @DisplayName("хозяин комнаты — тот, кто записан в createdBy сейчас, а не тот, кто её создавал")
    void hostIsTheCurrentCreatedBy() {
        RoomAccessGuard guard = guard(rooms(), FakeRoomRows.empty());
        Room room = Room.builder().id(ROOM).createdBy(Users.BOB).build();
        // Комната передана Боре: прежний хозяин распоряжаться ею больше не вправе.
        guard.requireHost(member, room);
        refuses("HOST_ONLY", 403, () -> guard.requireHost(host, room));
    }

    @Test
    @DisplayName("участник комнаты не хозяин: это разные права")
    void memberIsNotHost() {
        RoomAccessGuard guard = guard(
                rooms().room(ROOM, Users.ANN).player(ROOM, Users.BOB, "Боря"),
                FakeRoomRows.empty().room(ROOM, Users.ANN));
        refuses("HOST_ONLY", 403, () -> guard.requireHostRoomForWrite(member, ROOM));
        assertThat(guard.requireHostRoomForWrite(host, ROOM).getId()).isEqualTo(ROOM);
    }

    @Test
    @DisplayName("несуществующая комната — 404, и это не то же самое, что отказ в праве")
    void missingRoomIsNotFound() {
        RoomAccessGuard guard = guard(rooms(), FakeRoomRows.empty());
        refuses("ROOM_NOT_FOUND", 404, () -> guard.requireRoom(ROOM));
        refuses("ROOM_NOT_FOUND", 404, () -> guard.requireRoomForWrite(ROOM));
    }

    @Test
    @DisplayName("пишущий сценарий берёт комнату под замком, а не обычным чтением")
    void writeGoesThroughTheLock() {
        // Комната есть у обычного чтения и отсутствует у замка: сценарий,
        // прочитавший её мимо замка, вернул бы строку вместо 404.
        FakeRoomRows rows = FakeRoomRows.empty();
        RoomAccessGuard guard = guard(rooms().room(ROOM, Users.ANN), rows);
        refuses("ROOM_NOT_FOUND", 404, () -> guard.requireRoomForWrite(ROOM));
        assertThat(rows.locks()).isEqualTo(1);
    }

    private static FakeRooms rooms() {
        return FakeRooms.empty();
    }

    private static RoomAccessGuard guard(FakeRooms rooms, FakeRoomRows rows) {
        return new RoomAccessGuard(rooms, rows);
    }
}
