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
 * Одноразовая ссылка восстановления пароля.
 *
 * <p>Имя класса не {@code PasswordResetToken}: так называется сущность старой
 * схемы. Таблица — {@code v2.password_reset_token}.
 *
 * <p>{@link #usedAt} и {@link #invalidatedAt} разведены не ради красоты:
 * «ссылку уже применили» и «её отменил новый запрос» — разные ответы человеку,
 * который жмёт на письмо недельной давности. Одноразовость держит условный
 * {@code UPDATE … WHERE used_at IS NULL}, а не чтение перед записью.
 */
@Entity
@Table(name = "password_reset_token", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PasswordResetTicket {

    /** Хеш значения из письма. {@code @JsonIgnore} — та же находка B4. */
    @JsonIgnore
    @Id
    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /** Проставляется, когда ссылку обесценил более поздний запрос. */
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "requested_ip", length = 45)
    private String requestedIp;

    boolean usable(Instant now) {
        return usedAt == null && invalidatedAt == null
                && expiresAt != null && expiresAt.isAfter(now);
    }
}
