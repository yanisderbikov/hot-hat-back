package ru.hothat.room.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Составной ключ сердцебиения места; публичный по требованию JPA. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SeatPresenceId implements Serializable {

    private String roomId;

    private UUID playerId;
}
