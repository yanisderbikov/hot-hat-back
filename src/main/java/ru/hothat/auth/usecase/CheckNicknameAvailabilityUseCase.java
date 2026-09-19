package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.auth.api.dto.NicknameAvailabilityQueryDTO;
import ru.hothat.auth.api.dto.NicknameAvailabilityResponseDTO;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.util.Ids;

/**
 * Свободен ли ник.
 *
 * <p>Живёт в {@code auth}, потому что спрашивает его форма регистрации — до
 * того, как учётка существует. Отвечает при этом область профиля: имя лежит
 * в её таблице, и второго источника правды о занятости у формы регистрации
 * быть не должно.
 *
 * <p>У ника, не прошедшего по форме, {@code available} всегда {@code false}:
 * свободен он или нет, занять его всё равно нельзя.
 */
@Service
@RequiredArgsConstructor
public class CheckNicknameAvailabilityUseCase {

    private final PlayerCardPort cards;

    @PreAuthorize("permitAll()")
    public NicknameAvailabilityResponseDTO run(NicknameAvailabilityQueryDTO query) {
        String nickname = query.nickname().trim();
        if (!Ids.NICKNAME.matcher(nickname).matches()) {
            return new NicknameAvailabilityResponseDTO(nickname, false, false);
        }
        return new NicknameAvailabilityResponseDTO(nickname, true, !cards.nicknameTaken(nickname));
    }
}
