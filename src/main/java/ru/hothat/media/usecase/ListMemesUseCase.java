package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.MemeCatalogPageResponseDTO;
import ru.hothat.media.api.dto.MemeCardView;
import ru.hothat.media.api.dto.MemeCatalogQueryDTO;
import ru.hothat.media.store.MemeStore;

import java.util.List;

/**
 * Показать общую библиотеку мемов.
 *
 * <p>Снятые с публикации и черновики сюда не попадают — это дело выборки, а
 * не отсева после чтения: иначе предел применялся бы к строкам, половину
 * которых всё равно выбросят.
 *
 * <p>Карточка мема, у которого нет файла в хранилище, из выдачи не убирается:
 * такие остались от прежней модели, где ролик лежал байтами в базе. У них
 * пустой {@code videoUrl}, и это видно в карточке. Молча вычёркивать их
 * значило бы отдавать сорок карточек на запрос о шестидесяти и не давать
 * способа отличить конец библиотеки от конца выборки.
 */
@Service
@RequiredArgsConstructor
public class ListMemesUseCase {

    private final MemeStore memes;
    private final MemeCardMapper cards;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public MemeCatalogPageResponseDTO run(HotHatUser user, MemeCatalogQueryDTO query) {
        // Единственное место умолчания: параметров может не быть вовсе,
        // и «не задано» не должно разъезжаться по контроллеру и мапперу.
        int limit = query == null ? MemeCatalogQueryDTO.DEFAULT_LIMIT : query.limitOrDefault();
        List<MemeCardView> items = cards.cards(memes.catalog(limit), user.uid());
        return new MemeCatalogPageResponseDTO(items, null, limit);
    }
}
