package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.MemeCatalogPageResponseDTO;
import ru.hothat.media.usecase.ListMemesUseCase;
import ru.hothat.realtime.api.dto.MemeLibraryWindowView;

/**
 * Показать библиотеку мемов слушателю канала {@code /ws/v2/media/memes}.
 *
 * <p>Сценарий один и тот же для обоих кадров. Библиотеку собирает тот же
 * {@link ListMemesUseCase}, что отвечает на {@code GET /api/v2/media/memes},
 * и собирает её <b>на слушателя</b>: в карточке есть признак «мой», от
 * которого зависит кнопка удаления. Общий снимок на всех был бы здесь ошибкой,
 * незаметной до первой чужой кнопки.
 */
@Service
@RequiredArgsConstructor
public class StreamMemeLibraryUseCase {

    private final ListMemesUseCase listMemes;

    @PreAuthorize("hasRole('USER')")
    public MemeLibraryWindowView run(HotHatUser user) {
        // Параметров у канала нет: предел выбирает сервер, тот же, что у
        // страницы HTTP по умолчанию.
        MemeCatalogPageResponseDTO page = listMemes.run(user, null);
        return new MemeLibraryWindowView(page.items(), page.nextCursor(), page.limit());
    }
}
