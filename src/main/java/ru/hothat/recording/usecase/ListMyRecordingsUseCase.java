package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.MyRecordingQueryDTO;
import ru.hothat.recording.api.dto.MyRecordingsPageResponseDTO;
import ru.hothat.recording.store.RecordingStore;

import java.util.List;
import java.util.UUID;

/**
 * Личная библиотека записей.
 *
 * <p>Порядок задаёт база — индекс {@code ix_recording_save_player} по времени
 * сохранения. Раньше сотня карточек читалась целиком, сортировалась в памяти
 * по максимуму из двух отметок времени и по дороге у каждой спрашивался
 * LiveKit; так одно открытие профиля превращалось в двести сетевых вызовов
 * под одним соединением из пула (находка B5). Сверять здесь нечего:
 * сохранённая запись давно завершена.
 *
 * <p>Чтений в цикле нет: сколько бы записей ни было в библиотеке, это один
 * запрос за списком и по одному на каждую из таблиц карточки.
 */
@Service
@RequiredArgsConstructor
public class ListMyRecordingsUseCase {

    /** Столько записей отдаём по умолчанию; потолок задаёт сам запрос. */
    static final int DEFAULT_LIMIT = 50;

    private final RecordingStore store;
    private final RecordingCards cards;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public MyRecordingsPageResponseDTO run(HotHatUser user, MyRecordingQueryDTO query) {
        // Единственное место дефолта: параметров может не прийти вовсе, и
        // «не задано» не должно разъезжаться по контроллеру и переводчику.
        int limit = query == null || query.limit() == null ? DEFAULT_LIMIT : query.limit();
        List<UUID> mine = store.savedBy(user.uid(), limit);
        return new MyRecordingsPageResponseDTO(cards.cards(store.cards(mine), limit), null, limit);
    }
}
