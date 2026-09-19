package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.game.api.dto.ServerClockResponseDTO;

import java.time.Clock;

/**
 * Отдать серверное время.
 *
 * <p>Заменяет калибровку записью отметки в свою строку и немедленным чтением
 * её обратно ({@code app-core.js:8053-8062}) — то есть запись в базу ради
 * вопроса «который час». Все сроки партии заданы в этом времени.
 *
 * <p>Без токена: часы спрашивает и страница записи, которую открывает браузер
 * Egress, и главная до входа. Секрета в текущем времени нет.
 */
@Service
@RequiredArgsConstructor
public class ReadServerClockUseCase {

    private final Clock clock;

    @PreAuthorize("permitAll()")
    public ServerClockResponseDTO run() {
        return new ServerClockResponseDTO(clock.millis());
    }
}
