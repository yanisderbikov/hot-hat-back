package ru.hothat.media.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Файл мема в хранилище: ролик или заставка.
 *
 * <p>Отдельная сущность, потому что у файла свой цикл, не совпадающий с
 * карточкой: билет выдан ({@code pending}, со сроком) → объект залит
 * ({@code ready}) → владелец перелил ролик заново ({@link #revision} + 1).
 * Сегодня это шесть колонок внутри карточки, и перезаливка ролика переписывала
 * карточку целиком — вместе с названием, статусом и признаком встроенности.
 *
 * <p>Билета как отдельной сущности нет намеренно (§6.3): билет — это ровно
 * строка в состоянии {@code pending} со сроком {@link #uploadExpiresAt}.
 * Подписанная ссылка не хранится: она выводится из ключа и живёт минуты, а
 * сохранённая подпись означала бы вечную ссылку на приватный объект.
 *
 * <p>{@link #storageKey} уникален по всей таблице: два мема, показывающие на
 * один объект, означали бы, что снятие одного гасит ролик другого.
 *
 * <p>{@code @Version} нет: {@link #revision} — атомарный счётчик (§6.4),
 * а не версия строки. Он инкрементится одним оператором, без чтения в память.
 */
@Entity
@Table(name = "meme_asset", schema = "v2")
@IdClass(MemeAssetId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MemeAsset {

    /** Что за файл; набор закрыт ограничением базы. */
    static final String VIDEO = "video";
    static final String POSTER = "poster";

    /** Стадия жизни файла; набор закрыт ограничением базы. */
    static final String PENDING = "pending";
    static final String READY = "ready";

    @Id
    @Column(name = "meme_id", nullable = false, updatable = false)
    private UUID memeId;

    @Id
    @Column(nullable = false, updatable = false, length = 8)
    private String kind;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Builder.Default
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = PENDING;

    /** Срок билета; у готового файла пуст. */
    @Column(name = "upload_expires_at")
    private Instant uploadExpiresAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer revision = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
