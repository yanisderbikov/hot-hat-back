package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ChangeUiLanguageRequestDTO;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.profile.api.dto.UiLanguageResponseDTO;
import ru.hothat.profile.store.ProfileStore;

/**
 * Переключить язык интерфейса.
 *
 * <p>Допустимы только язык своего дивизиона и английский: переводы интерфейса
 * есть для всех девяти языков, но игрок, читающий слова партии по-русски,
 * с китайским интерфейсом остаётся без единой понятной подписи. Просьба о
 * третьем языке возвращает язык дивизиона — это видно по ответу, где едут оба.
 */
@Service
@RequiredArgsConstructor
public class ChangeUiLanguageUseCase {

    private final ProfileStore profiles;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public UiLanguageResponseDTO run(HotHatUser user, ChangeUiLanguageRequestDTO request) {
        ProfileStore.UiLanguages languages =
                profiles.setUiLanguage(user.uid(), request.uiLanguage().wireValue());
        return new UiLanguageResponseDTO(
                DivisionLanguage.fromWire(languages.uiLanguage()),
                DivisionLanguage.fromWire(languages.divisionLanguage()));
    }
}
