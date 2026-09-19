package ru.hothat.friend.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Заявка в друзья.
 *
 * <p>Свой числовой идентификатор остаётся: им отвечают на заявку, и он уже
 * объявлен числом в спецификации.
 *
 * <p>Направление и пара — разные вещи, и раньше они хранились двумя способами
 * сразу: {@code from_uid}/{@code to_uid} плюс склеенный {@code pair}. Здесь
 * направление — {@link #requester} и {@link #addressee}, а пара —
 * {@link #playerLow}/{@link #playerHigh}, которые считает база. Разойтись они
 * не могут, поэтому поля только читаются: они существуют ради уникального
 * индекса «одна живая заявка на пару», закрывающего сегодняшнюю гонку двух
 * встречных приглашений.
 *
 * <p>Ников здесь нет: {@code from_nickname}/{@code to_nickname} были копией
 * карточки игрока и устаревали при первой же смене ника.
 */
@Entity
@Table(name = "friend_request", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class FriendshipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID requester;

    @Column(nullable = false, updatable = false)
    private UUID addressee;

    /** pending | accepted | declined — набор закрыт ограничением базы. */
    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "player_low", insertable = false, updatable = false)
    private UUID playerLow;

    @Column(name = "player_high", insertable = false, updatable = false)
    private UUID playerHigh;
}
