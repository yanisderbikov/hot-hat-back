package ru.hothat.profile.store;

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
 * Заявка на ник, который занять самому не удалось.
 *
 * <p>Наследует {@code support_request}, потерявшую по дороге {@code type} и
 * {@code destination}: §6.3 числит их мёртвыми, а вид заявки всегда был один —
 * «хочу этот ник».
 *
 * <p>{@link #currentNickname} — снимок, а не ссылка на карточку: пока владелец
 * сервиса разбирает заявку, игрок может сменить ник, и разбирать тогда будет
 * нечего. «Было» обязано пережить «стало».
 *
 * <p>Открытая заявка у игрока одна — это держит частичный уникальный индекс,
 * иначе нетерпеливый игрок кладёт в очередь десять просьб об одном и том же.
 */
@Entity
@Table(name = "nickname_change_request", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class NicknameRequest {

    /** new | approved | declined — набор закрыт ограничением базы. */
    static final String NEW = "new";
    static final String APPROVED = "approved";
    static final String DECLINED = "declined";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Column(name = "current_nickname", updatable = false, length = 20)
    private String currentNickname;

    @Column(name = "requested_nickname", nullable = false, updatable = false, length = 20)
    private String requestedNickname;

    @Column(updatable = false, length = 500)
    private String reason;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = NEW;

    /**
     * Кто разобрал заявку. Без внешнего ключа: авторство решения обязано
     * пережить учётку разобравшего (см. шапку миграции V13).
     */
    @Column(name = "decided_by")
    private UUID decidedBy;

    /** Появляется вместе с решением: этого требует ограничение базы. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
