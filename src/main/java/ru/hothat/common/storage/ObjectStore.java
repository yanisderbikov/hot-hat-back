package ru.hothat.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * S3-совместимое хранилище: мем-ролики и MP4-записи партий.
 *
 * <p>Клиент общий на три области, и это не общая свалка: складывают файлы
 * трое — мемы, записи и уборка администратора, — но КУДА и под каким именем,
 * решает каждая у себя, в своём {@code store}. Здесь только подпись ссылок и
 * вызовы SDK; ни одного правила про ключ объекта в этом классе нет.
 *
 * <p>Бины SDK создаются только при заданных ключах, поэтому клиент спрашивает
 * их через {@link ObjectProvider}: без настроек приложение стартует, а адреса
 * медиа отвечают 503.
 */
@Slf4j
@Component
public class ObjectStore {

    private final ObjectProvider<S3Client> clientProvider;
    private final ObjectProvider<S3Presigner> presignerProvider;
    private final String bucket;
    private final String endpoint;

    public ObjectStore(ObjectProvider<S3Client> clientProvider,
                       ObjectProvider<S3Presigner> presignerProvider,
                       @Value("${s3.bucket:}") String bucket,
                       @Value("${s3.endpoint:}") String endpoint) {
        this.clientProvider = clientProvider;
        this.presignerProvider = presignerProvider;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    /** Заданы ли ключи хранилища; без них медиа-адреса отвечают 503. */
    public boolean configured() {
        return !bucket.isBlank() && clientProvider.getIfAvailable() != null;
    }

    public String bucket() {
        return bucket;
    }

    public String endpoint() {
        return endpoint;
    }

    private S3Client client() {
        S3Client client = clientProvider.getIfAvailable();
        if (client == null || bucket.isBlank()) {
            throw ApiException.of("S3_MEDIA_STORAGE_NOT_CONFIGURED", 503);
        }
        return client;
    }

    private S3Presigner presigner() {
        S3Presigner presigner = presignerProvider.getIfAvailable();
        if (presigner == null || bucket.isBlank()) {
            throw ApiException.of("S3_MEDIA_STORAGE_NOT_CONFIGURED", 503);
        }
        return presigner;
    }

    /** Подписанная ссылка на скачивание. */
    public String presignGet(String key, Duration ttl, String contentType, String contentDisposition,
                             String cacheControl) {
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (contentType != null) {
            request.responseContentType(contentType);
        }
        if (contentDisposition != null) {
            request.responseContentDisposition(contentDisposition);
        }
        if (cacheControl != null) {
            request.responseCacheControl(cacheControl);
        }
        return presigner().presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(request.build())
                .build()).url().toString();
    }

    /** Подписанная ссылка на загрузку — клиент кладёт файл сам, минуя бекенд. */
    public String presignPut(String key, Duration ttl, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        return presigner().presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(request)
                .build()).url().toString();
    }

    public void put(String key, byte[] body, String contentType) {
        client().putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(body));
    }

    public void delete(String key) {
        client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** Размер объекта; пусто, если объекта ещё нет. */
    public Optional<Long> contentLength(String key) {
        try {
            return Optional.ofNullable(client()
                    .headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
                    .contentLength());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Постранично, потому что в бакете может лежать сколько угодно объектов,
     * а нам нужен предсказуемый потолок.
     */
    public List<StoredObject> list(String prefix, int limit) {
        List<StoredObject> result = new ArrayList<>();
        String continuation = null;
        do {
            ListObjectsV2Response page = client().listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .maxKeys(Math.min(1000, Math.max(1, limit - result.size())))
                    .continuationToken(continuation)
                    .build());
            page.contents().forEach(item -> result.add(
                    new StoredObject(item.key(), item.size() == null ? 0 : item.size(), item.lastModified())));
            continuation = Boolean.TRUE.equals(page.isTruncated()) && result.size() < limit
                    ? page.nextContinuationToken() : null;
        } while (continuation != null && result.size() < limit);
        return result;
    }

    /** Объект бакета: ключ, размер и время изменения. */
    public record StoredObject(String key, long size, Instant lastModified) {
    }
}
