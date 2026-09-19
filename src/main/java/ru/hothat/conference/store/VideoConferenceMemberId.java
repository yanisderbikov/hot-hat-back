package ru.hothat.conference.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Составной ключ участия. Публичный, потому что этого требует JPA к классу
 * ключа; за пределами области им никто не пользуется.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class VideoConferenceMemberId implements Serializable {

    private String conferenceId;

    private UUID player;
}
