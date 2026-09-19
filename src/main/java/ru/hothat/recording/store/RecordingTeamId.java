package ru.hothat.recording.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Составной ключ команды записи; публичный по требованию JPA. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RecordingTeamId implements Serializable {

    private UUID recordingId;

    private UUID roomTeamId;
}
