package ru.hothat.game.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Составной ключ голоса; публичный по требованию JPA.
 *
 * <p>Три колонки, а не две из §6.2, — следствие составного ключа
 * {@link TurnWord}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AppealVoteId implements Serializable {

    private UUID turnId;

    private String wordId;

    private UUID voterPlayerId;
}
