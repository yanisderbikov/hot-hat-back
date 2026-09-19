package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ActiveRoomResponseDTO;
import ru.hothat.profile.spi.PlayerCardPort;

/**
 * Прочитать свою метку комнаты.
 *
 * <p>Единственная причина, по которой метка вообще существует: вернуть
 * человека в идущую партию, когда локальная память браузера пуста — новое
 * устройство, режим инкогнито, очищенный кеш. В этом случае клиенту неоткуда
 * взять комнату, кроме как спросить сервер, и адрес для вопроса нужен.
 *
 * <p>Это единственная операция сверх двадцати, перечисленных в §5.2 плана.
 * Раньше на её месте стояло прямое чтение документа {@code users/{uid}} через
 * прежний документный шлюз; вместе с переездом карточки такое чтение
 * перестало возвращать метку, а без неё восстановление партии работает только
 * на том же устройстве, где партия началась.
 *
 * <p>Метки может не быть: тогда {@code roomId} пуст, а не 404 — «нигде не
 * играю» это нормальное состояние, а не отсутствие ресурса.
 */
@Service
@RequiredArgsConstructor
public class GetMyActiveRoomUseCase {

    private final PlayerCardPort cards;

    @PreAuthorize("hasRole('USER')")
    public ActiveRoomResponseDTO run(HotHatUser user) {
        return cards.card(user.uid())
                .map(card -> new ActiveRoomResponseDTO(card.activeRoomId(), card.lastSeenAtMs()))
                .orElseGet(() -> new ActiveRoomResponseDTO(null, 0L));
    }
}
