package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.DeleteOrphanFileRequestDTO;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.store.MemeFileStorage;

/**
 * Убрать свой файл, оставшийся без карточки.
 *
 * <p>Владение проверяется по самому ключу: свой идентификатор сервер положил
 * туда, когда выдавал билет на загрузку. Ответа нет — старый {@code {ok:true}}
 * не нёс ничего, чего не было бы в запросе.
 */
@Service
@RequiredArgsConstructor
public class DeleteOrphanFileUseCase {

    private final MemeFileStorage files;

    @PreAuthorize("hasRole('USER')")
    public void run(HotHatUser user, DeleteOrphanFileRequestDTO request) {
        files.delete(MemeAssetKey.requireOwnedBy(request.storagePath(), user.uid()));
    }
}
