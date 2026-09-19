package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.profile.api.dto.LockDivisionRequestDTO;
import ru.hothat.profile.api.dto.LockedDivisionResponseDTO;
import ru.hothat.profile.store.ProfileStore;

/**
 * Закрепить дивизион за учётной записью.
 *
 * <p>Выбор однократный: повтор с другим кодом отвечает 409
 * {@code DIVISION_LOCKED}. Смена дивизиона перенесла бы очки игрока в чужую
 * таблицу, где он их не набирал, поэтому запрет стоит на сервере.
 *
 * <p>Однократность держит условный {@code UPDATE … WHERE division_locked_at
 * IS NULL} в хранилище, а не сравнение в коде: сравнение стояло в двух местах
 * сразу, и оба читали строку перед записью.
 */
@Service
@RequiredArgsConstructor
public class LockDivisionUseCase {

    private final ProfileStore profiles;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public LockedDivisionResponseDTO run(HotHatUser user, LockDivisionRequestDTO request) {
        String division = profiles.lockDivision(user.uid(), request.divisionLanguage().wireValue());
        return new LockedDivisionResponseDTO(DivisionLanguage.fromWire(division));
    }
}
