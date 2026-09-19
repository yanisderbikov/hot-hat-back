package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.MatchTicketStatusResponseDTO;

/**
 * Узнать, что стало с заявкой.
 *
 * <p>Заменяет повторный вызов подбора ради опроса раз в три-пять секунд
 * ({@code home/home.js:104}, {@code portal.js:139}). Это чистое чтение: оно
 * ничего не пересобирает, никого никуда не переселяет и стоит одного
 * обращения к строке комнаты вместо перебора восьмидесяти комнат фазы
 * {@code setup} на каждый тик таймера у каждого ищущего игрока.
 *
 * <p>Одна заявка читается одинаково и для быстрой игры, и для рейтинговой:
 * различается их подача, а не наблюдение за ними.
 */
@Service
@RequiredArgsConstructor
public class GetMyMatchTicketUseCase {

    private final MatchTicketDirectory tickets;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public MatchTicketStatusResponseDTO run(HotHatUser user, String ticketId) {
        return new MatchTicketStatusResponseDTO(tickets.requireMine(ticketId, user.uid()));
    }
}
