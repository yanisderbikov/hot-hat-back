package ru.hothat.app.usecase;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.app.api.dto.HealthResponseDTO;

/**
 * Ответить, что процесс жив.
 *
 * <p>Ни одной зависимости — и это главное свойство сценария, а не бедность.
 * Проба живости обязана отвечать быстро и одинаково, пока жив сам процесс:
 * стоит завести здесь чтение базы, и падение базы начнёт выглядеть как
 * падение сервиса, балансировщик снимет живые узлы, а вместе с ними исчезнет
 * и та часть API, которая базы не касается.
 *
 * <p>Готовность интеграций (LiveKit, хранилище, почта) — другой вопрос с
 * другой аудиторией; ему место в поверхности мониторинга, где есть права.
 */
@Service
public class CheckHealthUseCase {

    /** Имя, под которым процесс известен в общем мониторинге. */
    private static final String SERVICE = "hot-hat-back";

    /** Единственное состояние, которое эта проба умеет назвать вслух. */
    private static final String UP = "up";

    @PreAuthorize("permitAll()")
    public HealthResponseDTO run() {
        return new HealthResponseDTO(SERVICE, UP);
    }
}
