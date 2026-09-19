package ru.hothat.room.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Приглашение друга в комнату.
 *
 * <p>Имя класса не {@code RoomInvite}: так называется легаси-сущность.
 *
 * <p>Здесь нет ни {@code room_name}, ни {@code from_nickname}: §6.3 числит их
 * копиями чужих данных, и они устаревали при первом же переименовании.
 *
 * <p>Ссылки на сообщение чата тоже нет, хотя §6.2 плана её называет: обратная
 * ссылка уже есть — {@code v2.direct_chat_message.room_invite_id}, заведённая
 * V11. Две взаимные ссылки на один факт — ровно то, что этот план убирает из
 * схемы.
 *
 * <p>{@link #state} хранит три значения из девяти, что перечисляет
 * {@code RoomInviteState}: остальные шесть — ответы проекции на вопрос «можно
 * ли войти сейчас», и вычисляются они из комнаты и её жизненного цикла.
 *
 * <p>{@link #gameNumberAtInvite} гасит приглашение, выданное до старта
 * партии: войти по нему в уже начатую игру нельзя.
 */
@Entity
@Table(name = "room_invite", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomInvitation {

    /** pending | accepted | expired — набор закрыт ограничением базы. */
    static final String PENDING = "pending";
    static final String ACCEPTED = "accepted";
    static final String EXPIRED = "expired";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false, length = 24)
    private String roomId;

    @Column(name = "from_player_id", nullable = false, updatable = false)
    private UUID fromPlayerId;

    @Column(name = "to_player_id", nullable = false, updatable = false)
    private UUID toPlayerId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = PENDING;

    @Builder.Default
    @Column(name = "game_number_at_invite", nullable = false, updatable = false)
    private Integer gameNumberAtInvite = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
