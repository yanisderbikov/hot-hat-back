package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.LobbyRoomPreviewResponseDTO;
import ru.hothat.lobby.usecase.GetRoomPreviewUseCase;

/**
 * Показать выбранную комнату крупным планом — кадр {@code spotlight} канала лобби.
 *
 * <p>Собирает превью тот же {@link GetRoomPreviewUseCase}, что отвечает на
 * {@code GET /api/v2/lobby/rooms/{roomId}}. Форма одна на два транспорта, и
 * правило «текущее слово наружу не выходит» исполняется один раз — в нём.
 *
 * <p>Право проверяется на каждом кадре, а не однажды при переключении: комнату
 * могут сделать приватной или закрыть, пока превью открыто, и показ должен
 * прекратиться тогда же, а не через тридцать секунд, когда главная сама
 * переключится на следующую.
 */
@Service
@RequiredArgsConstructor
public class SpotlightLobbyRoomUseCase {

    private final GetRoomPreviewUseCase roomPreview;

    @PreAuthorize("hasAnyRole('GUEST','USER')")
    public LobbyRoomPreviewResponseDTO run(HotHatUser user, String roomId) {
        return roomPreview.run(user, roomId);
    }
}
