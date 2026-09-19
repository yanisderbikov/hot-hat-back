package ru.hothat.conference.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Правила видео-чата: пределы и срок — то, что раньше жило копиями в
 * шести файлах страницы и трёх функциях на Vercel.
 */
class ConferenceRulesTest {

    @Test
    @DisplayName("Приглашённые считаются вместе с вошедшими: шестнадцать обещанных мест — предел")
    void invitedCountTowardsCapacity() {
        assertThat(ConferenceRules.hasRoomFor(10, 5)).isTrue();
        assertThat(ConferenceRules.hasRoomFor(10, 6)).isFalse();
        assertThat(ConferenceRules.hasRoomFor(16, 0)).isFalse();
        assertThat(ConferenceRules.hasRoomFor(0, 0)).isTrue();
    }

    @Test
    @DisplayName("Видео-чат кончается либо закрытием, либо сроком")
    void expiredByStatusOrDeadline() {
        assertThat(ConferenceRules.expired(true, 2_000L, 1_000L)).isTrue();
        assertThat(ConferenceRules.expired(false, 1_000L, 1_000L)).isTrue();
        assertThat(ConferenceRules.expired(false, 2_000L, 1_000L)).isFalse();
        // Нулевой срок — «срока нет»: так читается пустая колонка у старых строк.
        assertThat(ConferenceRules.expired(false, 0L, 1_000L)).isFalse();
    }

    @ParameterizedTest(name = "{0} игроков → стол на {1}")
    @CsvSource({"1, 4", "2, 4", "4, 4", "5, 6", "6, 6", "7, 8", "8, 8", "9, 10", "10, 10", "16, 10"})
    @DisplayName("Вместимость игровой комнаты — ближайший чётный стол, не больше десяти")
    void gameRoomCapacityIsNextEvenTable(int players, int capacity) {
        assertThat(ConferenceRules.gameRoomCapacityFor(players)).isEqualTo(capacity);
    }

    @Test
    @DisplayName("Идентификатор — «vc-» и шестнадцать шестнадцатеричных знаков")
    void idShape() {
        assertThat(ConferenceRules.ID.matcher("vc-0f3a9c1d7b2e5480").matches()).isTrue();
        assertThat(ConferenceRules.ID.matcher("hat-0f3a9c1d7b2e5480").matches()).isFalse();
        assertThat(ConferenceRules.ID.matcher("vc-0F3A9C1D7B2E5480").matches()).isFalse();
        assertThat(ConferenceRules.ID.matcher("vc-0f3a9c1d7b2e548").matches()).isFalse();
    }

    @Test
    @DisplayName("Состояние участия читается по проводному имени и только по нему")
    void memberStatusFromWire() {
        assertThat(ConferenceRules.MemberStatus.fromWire("invited")).isEqualTo(ConferenceRules.MemberStatus.INVITED);
        assertThat(ConferenceRules.MemberStatus.fromWire("removed")).isEqualTo(ConferenceRules.MemberStatus.REMOVED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ConferenceRules.MemberStatus.fromWire("kicked"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
