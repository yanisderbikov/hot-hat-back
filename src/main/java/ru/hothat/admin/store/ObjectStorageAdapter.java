package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.admin.port.ObjectStoragePort;
import ru.hothat.common.storage.ObjectStore;

import java.util.Optional;

/**
 * Переходник к файловому хранилищу.
 *
 * <p>Единственное место области {@code admin}, знающее, что за хранилищем
 * стоит S3-совместимый сервис. Имя провайдера зашито здесь, а не в сценарии:
 * сценарий отвечает на вопрос «куда складываем», и смена хранилища не должна
 * его трогать.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectStorageAdapter implements ObjectStoragePort {

    private static final String PROVIDER = "s3-compatible";

    private final ObjectStore storage;

    @Override
    public boolean configured() {
        return storage.configured();
    }

    @Override
    public Optional<Target> target() {
        if (!storage.configured()) {
            return Optional.empty();
        }
        return Optional.of(new Target(PROVIDER, storage.bucket(), storage.endpoint()));
    }

    @Override
    public boolean delete(String objectPath) {
        if (objectPath == null || objectPath.isBlank() || !storage.configured()) {
            return false;
        }
        try {
            storage.delete(objectPath);
            return true;
        } catch (RuntimeException e) {
            // Осиротевший объект подметёт плановая сверка хранилища. Молча
            // оставить запись доступной было бы хуже, чем оставить файл.
            log.warn("Файл {} не удалён из хранилища: {}", objectPath, e.getMessage());
            return false;
        }
    }
}
