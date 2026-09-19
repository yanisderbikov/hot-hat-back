package ru.hothat.profile.store;

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
 * Аватар игрока отдельной строкой.
 *
 * <p>Сегодня это колонка {@code avatar_data_url} в карточке — до 120 000
 * символов в той самой строке, которую переписывает отметка присутствия. Из-за
 * неё же аватар тиражировался в места комнат, в состав зрителей и в таблицы
 * сезона (§6.3, «копии чужих данных»).
 *
 * <p>Хранится двоичным телом, а не data-URL: base64 стоит треть лишнего
 * объёма. Строка ответа собирается обратно из {@link #mediaType} и
 * {@link #bytes}, поэтому контракт ({@code avatarDataUrl}) не меняется.
 *
 * <p>{@link #sha256} — ETag ответа: без него список друзей тянул бы все
 * аватарки заново на каждом открытии. Считается при записи, расходиться не с
 * чем.
 */
@Entity
@Table(name = "player_avatar", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PlayerAvatar {

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** image/webp | image/jpeg | image/png — набор закрыт ограничением базы. */
    @Column(name = "media_type", nullable = false, length = 32)
    private String mediaType;

    /** До 92 160 байт: столько даёт предел в 120 000 символов base64. */
    @Column(nullable = false)
    private byte[] bytes;

    @Column(nullable = false)
    private byte[] sha256;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
