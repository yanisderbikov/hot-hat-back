package ru.hothat.lobby.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Кого с кем сводит подбор. */
class TicketMatchingTest {

    private static final long NOW = 1_000_000L;
    private static final long ALIVE = NOW + 60_000;

    private static TicketMatching.Option room(String id, int applicants, int ratingTarget) {
        return new TicketMatching.Option(id, true, false, true, false, "classic", 10, "ru", "ru",
                names(applicants), List.of("team-other"), ALIVE, ratingTarget);
    }

    private static List<String> names(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> "uid-" + i).toList();
    }

    private static TicketMatching.Wanted rankedPair(int rating) {
        return new TicketMatching.Wanted(true, "classic", 10, "ru", "ru",
                List.of("uid-me", "uid-partner"), "team-mine", rating);
    }

    @Test
    @DisplayName("Непонятный размер комнаты молча становится десяткой")
    void unknownSizeBecomesTen() {
        assertThat(TicketMatching.normalizeSize(4)).isEqualTo(4);
        assertThat(TicketMatching.normalizeSize(10)).isEqualTo(10);
        assertThat(TicketMatching.normalizeSize(7)).isEqualTo(TicketMatching.DEFAULT_SIZE);
        assertThat(TicketMatching.normalizeSize(0)).isEqualTo(TicketMatching.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("Комната, где заявка уже стоит, находится раньше свободных")
    void standingTicketWins() {
        TicketMatching.Option mine = new TicketMatching.Option("hat-mine", true, false, true, false,
                "classic", 10, "ru", "ru", List.of("uid-me"), List.of(), ALIVE, 1000);

        assertThat(TicketMatching.alreadyStanding(List.of(room("hat-other", 4, 1000), mine), rankedPair(1000)))
                .map(TicketMatching.Option::roomId).contains("hat-mine");
    }

    @Test
    @DisplayName("Рейтинговая пара идёт к ближайшему по очкам сопернику, а не к самой полной комнате")
    void rankedPrefersTheClosestRating() {
        TicketMatching.Option crowdedButFar = room("hat-far", 8, 1400);
        TicketMatching.Option emptyButClose = room("hat-close", 2, 1010);

        assertThat(TicketMatching.bestFit(List.of(crowdedButFar, emptyButClose), rankedPair(1000), NOW))
                .map(TicketMatching.Option::roomId).contains("hat-close");
    }

    @Test
    @DisplayName("Быстрая игра идёт в самую полную комнату: ей важно начать")
    void casualPrefersTheFullestRoom() {
        TicketMatching.Wanted quick = new TicketMatching.Wanted(false, "classic", 10, "ru", "ru",
                List.of("uid-me"), null, 0);
        TicketMatching.Option almostFull = new TicketMatching.Option("hat-full", false, false, true, false,
                "classic", 10, "ru", "ru", names(8), List.of(), ALIVE, 0);
        TicketMatching.Option nearlyEmpty = new TicketMatching.Option("hat-empty", false, false, true, false,
                "classic", 10, "ru", "ru", names(2), List.of(), ALIVE, 0);

        assertThat(TicketMatching.bestFit(List.of(nearlyEmpty, almostFull), quick, NOW))
                .map(TicketMatching.Option::roomId).contains("hat-full");
    }

    @Test
    @DisplayName("Соперник дальше четырёхсот очков не подходит")
    void ratingSpreadIsRespected() {
        TicketMatching.Option tooStrong = room("hat-strong", 4, 1000 + TicketMatching.RATING_SPREAD + 1);
        TicketMatching.Option atTheEdge = room("hat-edge", 4, 1000 + TicketMatching.RATING_SPREAD);

        assertThat(TicketMatching.bestFit(List.of(tooStrong), rankedPair(1000), NOW)).isEmpty();
        assertThat(TicketMatching.bestFit(List.of(atTheEdge), rankedPair(1000), NOW)).isPresent();
    }

    @Test
    @DisplayName("Пара не разлучается: комната, где для двоих нет места, не подходит")
    void pairIsNeverSplit() {
        TicketMatching.Option oneSeatLeft = room("hat-one-seat", 9, 1000);

        assertThat(TicketMatching.bestFit(List.of(oneSeatLeft), rankedPair(1000), NOW)).isEmpty();
    }

    @Test
    @DisplayName("Пара не сводится сама с собой")
    void pairDoesNotMeetItself() {
        TicketMatching.Option ownRoom = new TicketMatching.Option("hat-own", true, false, true, false,
                "classic", 10, "ru", "ru", names(2), List.of("team-mine"), ALIVE, 1000);

        assertThat(TicketMatching.bestFit(List.of(ownRoom), rankedPair(1000), NOW)).isEmpty();
    }

    @Test
    @DisplayName("Истёкшая комната уже никого не дождётся")
    void expiredRoomIsSkipped() {
        TicketMatching.Option expired = new TicketMatching.Option("hat-old", true, false, true, false,
                "classic", 10, "ru", "ru", names(2), List.of(), NOW, 1000);

        assertThat(TicketMatching.bestFit(List.of(expired), rankedPair(1000), NOW)).isEmpty();
    }

    @Test
    @DisplayName("Приватную и закрытую комнату подбор не трогает")
    void privateAndClosedRoomsAreSkipped() {
        TicketMatching.Option privateRoom = new TicketMatching.Option("hat-private", true, true, true, false,
                "classic", 10, "ru", "ru", names(2), List.of(), ALIVE, 1000);
        TicketMatching.Option closed = new TicketMatching.Option("hat-closed", true, false, true, true,
                "classic", 10, "ru", "ru", names(2), List.of(), ALIVE, 1000);

        assertThat(TicketMatching.bestFit(List.of(privateRoom, closed), rankedPair(1000), NOW)).isEmpty();
    }

    @Test
    @DisplayName("Рейтинговая пара не садится в комнату чужого дивизиона")
    void rankedRoomKeepsItsDivision() {
        TicketMatching.Option german = new TicketMatching.Option("hat-de", true, false, true, false,
                "classic", 10, "de", "ru", names(2), List.of(), ALIVE, 1000);

        assertThat(TicketMatching.bestFit(List.of(german), rankedPair(1000), NOW)).isEmpty();
    }

    @Test
    @DisplayName("Комната другого размера или другого режима не подходит")
    void setupMustMatch() {
        TicketMatching.Option otherSize = new TicketMatching.Option("hat-small", true, false, true, false,
                "classic", 4, "ru", "ru", names(2), List.of(), ALIVE, 1000);
        TicketMatching.Option otherMode = new TicketMatching.Option("hat-sabotage", true, false, true, false,
                "sabotage", 10, "ru", "ru", names(2), List.of(), ALIVE, 1000);

        assertThat(TicketMatching.bestFit(List.of(otherSize, otherMode), rankedPair(1000), NOW)).isEmpty();
    }
}
