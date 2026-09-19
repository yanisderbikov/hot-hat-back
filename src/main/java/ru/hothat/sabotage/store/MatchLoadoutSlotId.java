package ru.hothat.sabotage.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Составной ключ слота обоймы партии; публичный по требованию JPA. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MatchLoadoutSlotId implements Serializable {

    private UUID matchId;

    private UUID playerId;

    private Integer slotIndex;
}
