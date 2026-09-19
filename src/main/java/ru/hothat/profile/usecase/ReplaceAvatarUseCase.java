package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ReplaceAvatarRequestDTO;
import ru.hothat.profile.api.dto.StoredAvatarResponseDTO;
import ru.hothat.profile.store.ProfileStore;

/**
 * Заменить аватар текущего игрока.
 *
 * <p>Ответ возвращает то же изображение: клиент кладёт его в свой кеш, не
 * перечитывая карточку. Хранится оно двоичным телом в своей таблице, а
 * data-URL собирается на чтении — но контракт этого не замечает.
 */
@Service
@RequiredArgsConstructor
public class ReplaceAvatarUseCase {

    private final ProfileStore profiles;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public StoredAvatarResponseDTO run(HotHatUser user, ReplaceAvatarRequestDTO request) {
        return new StoredAvatarResponseDTO(profiles.replaceAvatar(user.uid(), request.avatarDataUrl()));
    }
}
