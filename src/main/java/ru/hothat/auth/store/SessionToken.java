package ru.hothat.auth.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Долгоживущая половина пары токенов, она же строка списка сессий.
 *
 * <p>Имя класса не {@code RefreshToken}: так называется сущность старой схемы,
 * а имена сущностей в JPA обязаны быть различны. Таблица при этом ровно
 * {@code v2.refresh_token}.
 *
 * <p>{@link #tokenHash} — SHA-256, тридцать два байта, а не шестьдесят четыре
 * символа шестнадцатеричной записи: по хешу никто не ищет глазами, а место в
 * таблице и в индексе он занимал вдвое большее. Самого значения токена в базе
 * нет вовсе — утечка таблицы не даёт войти ни за кого.
 *
 * <p>{@link #familyId} — цепочка ротаций одного входа. Без неё реюз
 * украденного токена (A11) нечем погасить: сегодня отзывается ровно
 * предъявленная строка, а вор продолжает крутить свою ветку цепочки.
 *
 * <p>{@link #replacedBy} — водяной знак без внешнего ключа, как
 * {@code last_read_message_id} в переписке: он обязан пережить уборку
 * истёкшей строки, на которую показывает.
 */
@Entity
@Table(name = "refresh_token", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SessionToken {

    /**
     * Хеш предъявляемого значения.
     *
     * <p>{@code @JsonIgnore} по той же причине, что у пароля и поколения
     * токенов (B4): даже хеш живого refresh-токена — материал для подбора, и
     * ни в один ответ он попасть не должен. Список сессий показывает
     * устройство и дату, а не ключ.
     */
    @JsonIgnore
    @Id
    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    /** На что этот токен обменяли при ротации; пусто — обмена ещё не было. */
    @JsonIgnore
    @Column(name = "replaced_by")
    private byte[] replacedBy;

    @Builder.Default
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * rotated | logout | logout_all | ban | reuse | password_change.
     * Набор не закрыт ограничением: это причина в журнале, а не состояние, и
     * новый повод отзыва не должен требовать миграции.
     */
    @Column(name = "revoked_reason", length = 24)
    private String revokedReason;

    /** 45 символов — предел текстовой записи IPv6. */
    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    /** Пригоден ли токен к обмену прямо сейчас. */
    boolean usable(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
