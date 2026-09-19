package ru.hothat.friend.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Подтверждённая дружба: одна строка на пару, ключ — сама пара.
 *
 * <p>Порядок в паре задан ограничением базы {@code player_low < player_high},
 * поэтому «Ваня и Петя» и «Петя и Ваня» физически не могут стать двумя
 * строками. Раньше эту же роль играла склеенная строка {@code pair}
 * VARCHAR(340), собранная {@code Ids.pair}: ключ приходилось строить в коде
 * до каждого обращения, и рядом с ним лежали те же два uid ещё дважды —
 * колонками и списком в jsonb.
 *
 * <p>Класс не публичный намеренно: сущность не выходит за пределы
 * {@code friend.store} — ни в сигнатуры сценариев, ни в DTO. Тот же приём,
 * что у {@code auth.store.UserBanRecords}.
 */
@Entity
@Table(name = "friendship", schema = "v2")
@IdClass(FriendshipId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Friendship {

    @Id
    @Column(name = "player_low", nullable = false, updatable = false)
    private UUID playerLow;

    @Id
    @Column(name = "player_high", nullable = false, updatable = false)
    private UUID playerHigh;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
