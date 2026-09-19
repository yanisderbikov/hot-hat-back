package ru.hothat.friend.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Составной ключ дружбы. Публичный, потому что этого требует JPA к классу
 * ключа; за пределами области им никто не пользуется.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FriendshipId implements Serializable {

    private UUID playerLow;

    private UUID playerHigh;
}
