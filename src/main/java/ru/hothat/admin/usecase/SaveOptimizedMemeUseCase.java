package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.OptimizedMemeResponseDTO;
import ru.hothat.admin.api.dto.SaveOptimizedMemeRequestDTO;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.spi.MemeModerationPort;

/**
 * Сохранить сжатую для мобильных версию мема.
 *
 * <p>Заменяет {@code POST /api/optimize-meme}. Адрес стал вложенным ресурсом
 * мема и методом {@code PUT}: сохранение оптимизированной версии
 * идемпотентно — второй прогон оптимизатора заменяет первый, а не создаёт
 * ещё одну версию.
 *
 * <p>Сжатый ролик уезжает в хранилище под тем же ключом, что и прежний:
 * адрес мема постоянный, и менять его при оптимизации значило бы разослать
 * всем ссылку на пустоту. Раньше байты оставались текстом в строке карточки,
 * и библиотека тянула их в память на каждую карточку.
 *
 * <p>Своей транзакции нет: сценарий кладёт файл в хранилище по сети, а запись
 * карточки и файла транзакционна внутри области медиа.
 */
@Service
@RequiredArgsConstructor
public class SaveOptimizedMemeUseCase {

    private static final String DEFAULT_MIME = "video/webm";
    private static final String DEFAULT_VERSION = "mobile-v1";

    private final MemeModerationPort memes;

    @PreAuthorize("hasRole('ADMIN')")
    public OptimizedMemeResponseDTO run(HotHatUser admin, String memeId,
                                        SaveOptimizedMemeRequestDTO request) {
        MemeModerationPort.Optimized saved = memes.saveOptimized(
                memeId,
                request.dataUrl(),
                blank(request.mime()) ? DEFAULT_MIME : request.mime(),
                request.durationMs(),
                blank(request.optimizedVersion()) ? DEFAULT_VERSION : request.optimizedVersion());
        return new OptimizedMemeResponseDTO(saved.memeId(), saved.bytes(), saved.contentType(),
                saved.durationMs(), saved.version(), saved.optimizedAtMs());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
