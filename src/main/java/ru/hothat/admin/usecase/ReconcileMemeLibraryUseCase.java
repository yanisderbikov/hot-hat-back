package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.MemeLibraryReconciliationResponseDTO;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.spi.MemeModerationPort;

/**
 * Сверить библиотеку мемов с хранилищем.
 *
 * <p>Заменяет {@code POST /api/meme-library-sync}, доступный сегодня любому
 * вошедшему. Операция перебирает до двух тысяч объектов бакета и заводит
 * карточки — это работа администратора, а не читателя каталога, и ни один
 * экран игрока её не зовёт.
 *
 * <p>Сверка нужна после сбоев загрузки, когда файл уже уехал в хранилище, а
 * метаданные записать не успели: без неё ролик существует, но библиотека о
 * нём не знает.
 *
 * <p>Своей транзакции здесь нет: сверка ходит в хранилище по сети, а запись
 * найденного транзакционна внутри области медиа.
 */
@Service
@RequiredArgsConstructor
public class ReconcileMemeLibraryUseCase {

    private final MemeModerationPort memes;

    @PreAuthorize("hasRole('ADMIN')")
    public MemeLibraryReconciliationResponseDTO run(HotHatUser admin) {
        MemeModerationPort.Reconciliation result = memes.reconcile();
        return new MemeLibraryReconciliationResponseDTO(
                result.videos(), result.recovered(), result.existing(), result.scannedObjects());
    }
}
