package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.AdminRecordingCardView;
import ru.hothat.admin.api.dto.AdminRecordingQueryDTO;
import ru.hothat.admin.api.dto.AdminRecordingsPageResponseDTO;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.media.GameRecording;
import ru.hothat.repository.GetterMedia;
import ru.hothat.util.Divisions;

import java.util.ArrayList;
import java.util.List;

/**
 * Показать администратору каталог записей.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=admin_list} —
 * ветку администратора внутри общего {@code case}, где право вычислялось прямо
 * в аргументе вызова ({@code RecordingsController:51}).
 *
 * <p>Две вещи, которые делал прежний движок, здесь не делаются, и обе — намеренно.
 *
 * <p><b>Уборка просроченных записей.</b> Открытие списка сносило до двадцати
 * файлов в хранилище. Чтение не должно ничего удалять: это работа планового
 * маршрута {@code /api/v2/machine/maintenance/recording-sweeps}, и у неё своё
 * право входа.
 *
 * <p><b>Опрос LiveKit по каждой незавершённой записи.</b> На строку списка
 * уходил сетевой вызов, то есть открытие каталога стоило до двухсот пятидесяти
 * обращений к чужой службе (B5). Состояние записи приносит вебхук Egress;
 * пока он не пришёл, в карточке честно стоит {@code active} — это и есть
 * правда о ней на момент чтения.
 */
@Service
@RequiredArgsConstructor
public class ListRecordingsForAdminUseCase {

    private final GetterMedia getterMedia;
    private final AdminRecordingMapper mapper;

    @PreAuthorize("hasRole('ADMIN')")
    public AdminRecordingsPageResponseDTO run(HotHatUser admin, AdminRecordingQueryDTO query) {
        int limit = query == null ? AdminRecordingQueryDTO.DEFAULT_LIMIT : query.limitOrDefault();
        String division = query == null ? null : query.division();

        List<AdminRecordingCardView> items = new ArrayList<>();
        for (GameRecording recording : getterMedia.getRecentRecordings(limit)) {
            if (!matches(recording, division)) {
                continue;
            }
            items.add(mapper.card(recording));
        }
        return new AdminRecordingsPageResponseDTO(items, null, limit);
    }

    /** Незаданный дивизион значит «все»: отдельного слова для этого не нужно. */
    private static boolean matches(GameRecording recording, String division) {
        return division == null || division.isBlank()
                || division.equals(Divisions.normalize(recording.getDivisionLanguage()));
    }
}
