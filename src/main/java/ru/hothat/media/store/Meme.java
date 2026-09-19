package ru.hothat.media.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Карточка мема.
 *
 * <p>{@link #id} — суррогат, а сегодняшний {@code meme-9f31ab77c204} стал
 * {@link #slug}. Так надо потому, что этот текст выбирает КЛИЕНТ ещё до
 * публикации: под него уже выдан билет и по нему построен путь объекта в
 * хранилище. Оставить его первичным ключом значило бы дать браузеру право
 * называть строку в базе.
 *
 * <p>{@link #status} держит три значения, и первого из них сегодня нет.
 * {@code draft} — это и есть билет на загрузку: §6.3 не заводит таблицы
 * билетов, потому что билет — строка {@link MemeAsset} в состоянии
 * {@code pending}, а у неё первичный ключ начинается с мема. Значит, выдача
 * билета заводит черновик карточки, и только публикация даёт ей название,
 * длительность и {@code active}.
 *
 * <p>{@code @Version} отсюда и берётся (§6.4): строка пишется не один раз —
 * черновик, публикация, оптимизация, снятие, — и писатели у этих шагов разные.
 *
 * <p>{@link #origin} — одно перечисление вместо двух булевых флагов
 * {@code builtin} и {@code recovered_from_s3}. Флаги позволяли выразить
 * бессмыслицу: встроенный мем, восстановленный сверкой с бакетом.
 *
 * <p>Полей {@code src}, {@code poster}, {@code mime}, {@code byteSize},
 * {@code mediaVersion} и {@code storageProvider} здесь нет: всё это состояние
 * ФАЙЛА, а у файла свой жизненный цикл — см. {@link MemeAsset}. Нет и
 * {@code dataUrl}: байты ролика в строке карточки заставляли чтение
 * библиотеки тянуть мегабайты в память на каждую карточку.
 *
 * <p>Колонки {@code title_search} в отображении нет намеренно: это
 * {@code tsvector}, который считает база, и в Java у него нет ни типа, ни
 * читателя — поиск идёт запросом, а не сравнением в памяти.
 */
@Entity
@Table(name = "meme", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Meme {

    /** Как мем попал в библиотеку; набор закрыт ограничением базы. */
    static final String PLAYER = "player";
    static final String BUILTIN = "builtin";
    static final String RECONCILED = "reconciled";

    /** Стадия жизни карточки; набор закрыт ограничением базы. */
    static final String DRAFT = "draft";
    static final String ACTIVE = "active";
    static final String WITHDRAWN = "withdrawn";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Тот самый идентификатор, что стоит в пути объекта: meme-… или builtin-…. */
    @Column(nullable = false, updatable = false, length = 180)
    private String slug;

    /** Пусто у черновика: название приходит с публикацией, а не с билетом. */
    @Column(length = 120)
    private String title;

    /** Столько же длится диверсия. Границы — те же, что в MemeAssetLimits. */
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Builder.Default
    @Column(name = "division_language", nullable = false, length = 8)
    private String divisionLanguage = "ru";

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String origin = PLAYER;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = DRAFT;

    /**
     * Владелец читается из пути объекта ровно так же, как его туда положил
     * сервер, поэтому он есть и у мема, восстановленного сверкой с бакетом.
     * Пусто только у встроенных: их заводит посев, а не игрок.
     */
    @Column(name = "owner_player_id", updatable = false)
    private UUID ownerPlayerId;

    /** Откуда нарезан ролик, если он пришёл по ссылке. */
    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    /** Свободная пометка интерфейса: file, record, direct-url и подобное. */
    @Column(name = "import_mode", length = 24)
    private String importMode;

    @Column(name = "optimized_version", length = 40)
    private String optimizedVersion;

    @Column(name = "optimized_at")
    private Instant optimizedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
