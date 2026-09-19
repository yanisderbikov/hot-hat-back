package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.SubmitNicknameRequestDTO;
import ru.hothat.profile.api.dto.SubmittedNicknameRequestResponseDTO;
import ru.hothat.profile.store.ProfileStore;

/**
 * Подать заявку на ник, который занять самому не вышло.
 *
 * <p>Заявку разбирает владелец сервиса вручную, автоматического решения нет.
 * Нынешний ник читается и заявка пишется в одной транзакции: иначе в ответе
 * могло бы оказаться имя, которое к моменту записи заявки уже сменилось,
 * и владелец увидел бы пару «было — просим» из двух разных мгновений.
 *
 * <p>Вторая открытая заявка того же игрока не заводится: её не примет
 * частичный уникальный индекс {@code ux_nickname_change_request_open}.
 */
@Service
@RequiredArgsConstructor
public class SubmitNicknameRequestUseCase {

    private final ProfileStore profiles;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SubmittedNicknameRequestResponseDTO run(HotHatUser user, SubmitNicknameRequestDTO request) {
        String current = profiles.card(user.uid())
                .map(ProfileStore.Card::nickname)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        profiles.openNicknameRequest(user.uid(), current, request.nickname(), request.reason());
        return new SubmittedNicknameRequestResponseDTO(current, request.nickname());
    }
}
