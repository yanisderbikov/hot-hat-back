package ru.hothat.admin.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Составной ключ накопленного трафика; публичный по требованию JPA. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrafficPeriodId implements Serializable {

    private String meterId;

    private String periodKind;

    private String periodKey;
}
