package ru.hothat.game.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Составной ключ исхода слова; публичный по требованию JPA.
 *
 * <p>Ключ составной потому, что {@code id} придумывает клиент: это ключ
 * идемпотентности нажатия, а не суррогат. Подробности — в {@link TurnWord}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TurnWordId implements Serializable {

    private UUID turnId;

    private String id;
}
