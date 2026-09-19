package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.MatchSize;
import ru.hothat.lobby.api.dto.OpenRankedTicketRequestDTO;
import ru.hothat.lobby.api.dto.RankedTicketResponseDTO;

/**
 * Поставить рейтинговую пару в подбор.
 *
 * <p>Заменяет {@code portal matchmake} с {@code ranked=true}
 * ({@code portal.js:127}). Пара занимает свободный слот команды целиком:
 * напарников не разлучают, поэтому в комнате освобождается место сразу под
 * двоих, а пары сводятся по близости рейтинга — не дальше четырёхсот очков.
 *
 * <p>Право капитана проверяет сам сценарий подбора, а не предикат на классе, и
 * это осознанно. В плане здесь стоит {@code @teamAuthz.isCaptain}, но пакета
 * безопасности ещё нет, а главное — ответ на «капитан ли ты» требует чтения
 * предматчевой подготовки, которое движок и так делает первым же шагом
 * ({@code requireLaunchablePreflight} плюс сверка с {@code initiatorUid},
 * {@code PREFLIGHT_CAPTAIN_ONLY} 403). Предикат уровня класса читал бы ту же
 * подготовку вторично — на опросе раз в три секунды это удвоенная работа ради
 * той же самой проверки.
 */
@Service
@RequiredArgsConstructor
public class OpenRankedTicketUseCase {

    private final MatchTicketBroker broker;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RankedTicketResponseDTO run(HotHatUser user, OpenRankedTicketRequestDTO request) {
        // Размер и язык рейтинговой комнаты выбирает не клиент: комната всегда
        // десятиместная, а язык — родной дивизион команды.
        return new RankedTicketResponseDTO(broker.open(
                user, true, request.gameMode(), MatchSize.RANKED.players(), null));
    }
}
