package ru.hothat.service.storage.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.hothat.common.storage.ObjectStore;
import ru.hothat.service.storage.ObjectStorageService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Прежний фасад хранилища: теперь только переходник к {@link ObjectStore}.
 *
 * <p>Работа с S3 переехала в общий клиент, к которому обращаются области.
 * Фасад жив, пока живы старые адреса медиа и записей.
 */
@Service
@RequiredArgsConstructor
public class S3ObjectStorageService implements ObjectStorageService {

    private final ObjectStore store;

    @Override
    public boolean isConfigured() {
        return store.configured();
    }

    @Override
    public String bucket() {
        return store.bucket();
    }

    @Override
    public String endpoint() {
        return store.endpoint();
    }

    @Override
    public String presignGet(String key, Duration ttl, String contentType, String contentDisposition,
                             String cacheControl) {
        return store.presignGet(key, ttl, contentType, contentDisposition, cacheControl);
    }

    @Override
    public String presignPut(String key, Duration ttl, String contentType) {
        return store.presignPut(key, ttl, contentType);
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
        store.put(key, body, contentType);
    }

    @Override
    public void delete(String key) {
        store.delete(key);
    }

    @Override
    public Optional<Long> contentLength(String key) {
        return store.contentLength(key);
    }

    @Override
    public List<StoredObject> list(String prefix, int limit) {
        return store.list(prefix, limit).stream()
                .map(item -> new StoredObject(item.key(), item.size(), item.lastModified()))
                .toList();
    }
}
