package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.MatchTicketResponseDTO;
import ru.hothat.lobby.api.dto.OpenCasualTicketRequestDTO;

/**
 * Подать заявку на быструю игру.
 *
 * <p>Подача — и только подача. Раньше этим же вызовом шёл и опрос, каждые
 * три секунды: {@code MatchmakingServiceImpl:96-127} на каждом вызове заново
 * перебирал комнаты фазы {@code setup}, из-за чего очередной опрос мог увести
 * игрока в другую комнату — а первая оставалась ждать его до конца срока.
 * Теперь заявка подаётся один раз, получает адрес и дальше читается.
 *
 * <p>Предусловия проверяются здесь же, в общем ходе подачи: заряженная обойма
 * мемов ({@code DEFAULT_LOADOUT_REQUIRED}, 409), лимит бесплатных партий с
 * диверсиями ({@code SABOTAGE_LIMIT_REACHED}, 402) и допустимый язык быстрой
 * игры ({@code QUICK_LANGUAGE_INVALID}, 409 — свой дивизион либо общий
 * английский).
 */
@Service
@RequiredArgsConstructor
public class OpenCasualTicketUseCase {

    private final MatchTicketBroker broker;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public MatchTicketResponseDTO run(HotHatUser user, OpenCasualTicketRequestDTO request) {
        // null здесь значит «язык моего дивизиона»: подставлять его тут было бы
        // вторым местом, где живёт это правило, — дивизион знает профиль.
        String language = request.gameLanguage() == null ? null : request.gameLanguage().wireValue();
        return new MatchTicketResponseDTO(broker.open(
                user, false, request.gameMode(), request.maxPlayers().players(), language));
    }
}
