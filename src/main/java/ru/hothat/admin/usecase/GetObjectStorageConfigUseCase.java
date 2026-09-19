package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.ObjectStorageConfigResponseDTO;
import ru.hothat.config.HotHatUser;
import ru.hothat.admin.port.ObjectStoragePort;

/**
 * Показать администратору, куда сервис складывает файлы.
 *
 * <p>Заменяет {@code POST /api/media} с {@code action=config_status} — ветку
 * на маршруте, лежавшем в {@code permitAll} (A12).
 *
 * <p>Ненастроенное хранилище здесь не ошибка, а ответ: прежний движок отвечал
 * на этот вопрос кодом 503, хотя спрашивали именно «настроено ли». Ошибку 503
 * по-прежнему получают те, кто пытается им пользоваться.
 */
@Service
@RequiredArgsConstructor
public class GetObjectStorageConfigUseCase {

    private final ObjectStoragePort storage;

    @PreAuthorize("hasRole('ADMIN')")
    public ObjectStorageConfigResponseDTO run(HotHatUser admin) {
        return storage.target()
                .map(target -> new ObjectStorageConfigResponseDTO(
                        true, target.provider(), target.bucket(), target.endpoint()))
                .orElseGet(() -> new ObjectStorageConfigResponseDTO(false, null, null, null));
    }
}
