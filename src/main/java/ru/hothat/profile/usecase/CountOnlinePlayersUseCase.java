package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.profile.api.dto.PresenceSummaryResponseDTO;
import ru.hothat.profile.store.ProfileStore;

import java.time.Instant;

/**
 * Посчитать, сколько игроков сейчас в портале.
 *
 * <p>Онлайн — те, кто отмечался за последние две минуты. Личность
 * спрашивающего на ответ не влияет, поэтому её здесь и нет; право входа
 * всё же нужно — число игроков наружу не показывается.
 *
 * <p>Минимум единица: сам спрашивающий. Считает база по индексу
 * {@code ix_player_presence_seen}; раньше тот же счёт шёл
 * последовательным чтением всей таблицы учёток.
 */
@Service
@RequiredArgsConstructor
public class CountOnlinePlayersUseCase {

    /** Два неполных пинга: клиент отмечается чаще, чем раз в минуту. */
    private static final long ONLINE_WINDOW_MS = 130_000;

    private final ProfileStore profiles;

    @PreAuthorize("hasRole('USER')")
    public PresenceSummaryResponseDTO run() {
        long now = System.currentTimeMillis();
        long online = profiles.onlineSince(Instant.ofEpochMilli(now - ONLINE_WINDOW_MS));
        return new PresenceSummaryResponseDTO(Math.max(1L, online), now);
    }
}
