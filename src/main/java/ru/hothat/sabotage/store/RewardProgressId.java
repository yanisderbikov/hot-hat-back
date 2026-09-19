package ru.hothat.sabotage.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Составной ключ прогресса команды; публичный по требованию JPA. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RewardProgressId implements Serializable {

    private UUID matchId;

    private UUID roomTeamId;
}
