package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.JoinRankedRoomRequestDTO;
import ru.hothat.lobby.api.dto.RankedRoomEntryResponseDTO;

/**
 * Ввести рейтинговую пару в названную комнату.
 *
 * <p>Заменяет {@code portal ranked_join_room}. Комната здесь не подбирается —
 * она названа заранее, на предматчевой подготовке, и сценарий сверяет четыре
 * вещи: та ли это комната, о которой договорились
 * ({@code PREFLIGHT_ROOM_MISMATCH}), жива ли она и рейтинговая ли
 * ({@code RANKED_ROOM_UNAVAILABLE}), тот ли режим
 * ({@code RANKED_ROOM_MODE_MISMATCH}) и тот ли дивизион
 * ({@code RANKED_ROOM_DIVISION_MISMATCH}).
 *
 * <p>Право капитана — там же, где у подачи заявки: внутри сценария, чтобы не
 * читать подготовку дважды. Подробности — в {@link OpenRankedTicketUseCase}.
 */
@Service
@RequiredArgsConstructor
public class JoinRankedRoomUseCase {

    private final MatchTicketBroker broker;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RankedRoomEntryResponseDTO run(HotHatUser user, String roomId, JoinRankedRoomRequestDTO request) {
        // Пара, вошедшая в названную комнату, занимает слот целиком — состав
        // для неё готов сразу, и второго ожидания у этого адреса нет.
        return new RankedRoomEntryResponseDTO(broker.joinRanked(user, roomId, request.gameMode()), true);
    }
}
