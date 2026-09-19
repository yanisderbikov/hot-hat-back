package ru.hothat.media.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.common.identity.LegacyIdBridge;
import ru.hothat.media.domain.MemeAssetKind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Единственная дверь области {@code media} в таблицы библиотеки мемов.
 *
 * <p>Наружу отдаёт записи, а не сущности: {@link Meme} и {@link MemeAsset} не
 * публичны. Здесь же живёт перевод «идентификатор мема ↔ uuid»: строку
 * {@code meme-9f31ab77c204} выбирает клиент ещё до публикации — под неё уже
 * выдан билет и по ней построен путь объекта, — поэтому ключом строки она быть
 * не может, а обойма диверсий ссылается на мем именно uuid'ом.
 *
 * <p>uuid считается из идентификатора той же формулой, что записана в
 * миграции ({@code md5('meme:' || slug)}), а не выдаётся случайно. Иначе
 * обоймы, собранные до переезда, показали бы на несуществующие мемы.
 *
 * <p>Карточка и файл разделены: у файла свой цикл — билет выдан, объект залит,
 * ролик перелит заново, — и перезаливка больше не переписывает название со
 * статусом заодно.
 */
@Component
@RequiredArgsConstructor
public class MemeStore {

    /** Стадии жизни карточки; набор закрыт ограничением базы. */
    public static final String DRAFT = Meme.DRAFT;
    public static final String ACTIVE = Meme.ACTIVE;
    public static final String WITHDRAWN = Meme.WITHDRAWN;

    private final Memes memes;
    private final MemeAssets assets;
    private final LegacyIdBridge ids;

    // ───────────────────────────── чтение ─────────────────────────────

    /** Карточка по её идентификатору вместе с файлами; пусто — такой нет. */
    @Transactional(readOnly = true)
    public Optional<Card> find(String memeId) {
        if (memeId == null || memeId.isBlank()) {
            return Optional.empty();
        }
        return memes.findBySlug(memeId).map(meme -> card(meme, assets.findByMemeId(meme.getId()), owners(List.of(meme))));
    }

    /**
     * Витрина библиотеки: свежие сверху, снятые и черновики не попадают.
     *
     * <p>Отбор делает база, а не отсев после чтения: иначе предел применялся
     * бы к строкам, половину которых всё равно выбросят, и страница молча
     * оказывалась бы неполной.
     */
    @Transactional(readOnly = true)
    public List<Card> catalog(int limit) {
        List<Meme> rows = memes.findByStatusOrderByCreatedAtDesc(Meme.ACTIVE, Limit.of(limit));
        if (rows.isEmpty()) {
            return List.of();
        }
        // Два чтения на страницу, а не два на карточку: файлы всех
        // шестидесяти мемов приезжают одной выборкой, владельцы — одной.
        Map<UUID, List<MemeAsset>> filesByMeme = new LinkedHashMap<>();
        for (MemeAsset asset : assets.findByMemeIdIn(rows.stream().map(Meme::getId).toList())) {
            filesByMeme.computeIfAbsent(asset.getMemeId(), key -> new ArrayList<>()).add(asset);
        }
        Map<UUID, String> owners = owners(rows);
        List<Card> cards = new ArrayList<>(rows.size());
        for (Meme meme : rows) {
            cards.add(card(meme, filesByMeme.getOrDefault(meme.getId(), List.of()), owners));
        }
        return cards;
    }

    /**
     * Какие из названных мемов годятся к показу — одним чтением.
     *
     * <p>Годится только выложенный: черновик ролика ещё не имеет, а снятый
     * его уже потерял. Прежний движок отвечал «есть» и на снятый тоже, отчего
     * обойма молча заряжалась мемом, который в бою показал бы чёрный экран.
     */
    @Transactional(readOnly = true)
    public Set<String> existing(Collection<String> memeIds) {
        List<String> wanted = distinct(memeIds);
        if (wanted.isEmpty()) {
            return Set.of();
        }
        Set<String> found = new LinkedHashSet<>();
        for (Meme meme : memes.findBySlugIn(wanted)) {
            if (Meme.ACTIVE.equals(meme.getStatus())) {
                found.add(meme.getSlug());
            }
        }
        return found;
    }

    // ───────────────────────────── запись ─────────────────────────────

    /**
     * Выдан билет на загрузку файла: заводится черновик карточки и строка
     * файла в состоянии {@code pending} со сроком.
     *
     * <p>Черновик — это и есть билет (§6.3): отдельной таблицы билетов нет,
     * потому что билет без файла и файл без билета одинаково бессмысленны.
     * Повторный билет на тот же файл продлевает срок и переписывает ключ, а
     * не заводит вторую строку: у файла ключ {@code (мем, вид)}.
     */
    @Transactional
    public void reserveUpload(String memeId, String ownerUid, String divisionLanguage,
                              MemeAssetKind kind, String storageKey, String contentType,
                              long sizeBytes, Instant expiresAt) {
        Meme meme = memes.findBySlug(memeId).orElseGet(() -> newCard(memeId, ownerUid, divisionLanguage));
        // Дивизион мог смениться между билетом на ролик и билетом на заставку:
        // путь объекта строится по нему, и расходиться им нельзя.
        meme.setDivisionLanguage(divisionLanguage);
        memes.save(meme);
        MemeAsset asset = assets.findById(assetId(meme.getId(), kind)).orElseGet(() -> MemeAsset.builder()
                .memeId(meme.getId())
                .kind(kindOf(kind))
                .build());
        asset.setStorageKey(storageKey);
        asset.setContentType(contentType);
        asset.setSizeBytes(Math.max(0, sizeBytes));
        asset.setState(MemeAsset.PENDING);
        asset.setUploadExpiresAt(expiresAt);
        asset.setUploadedAt(null);
        assets.save(asset);
    }

    /**
     * Опубликовать карточку: черновик становится выложенным, а его файлы —
     * готовыми.
     *
     * <p>Номер редакции ролика растёт только когда файл действительно другой:
     * по нему клиент сбрасывает свой кеш подписанных ссылок. Повтор публикации
     * с тем же файлом — это тот же мем, а не второй: сеть могла оборвать ответ
     * уже после записи.
     */
    @Transactional
    public Card publish(Publication publication, Instant now) {
        Meme meme = memes.findBySlug(publication.memeId())
                .orElseGet(() -> newCard(publication.memeId(), publication.ownerUid(), publication.divisionLanguage()));
        meme.setTitle(publication.title());
        meme.setDurationMs(publication.durationMs());
        meme.setDivisionLanguage(publication.divisionLanguage());
        meme.setSourceUrl(publication.sourceUrl());
        meme.setImportMode(publication.importMode());
        meme.setStatus(Meme.ACTIVE);
        meme.setWithdrawnAt(null);
        if (meme.getPublishedAt() == null) {
            meme.setPublishedAt(now);
        }
        memes.save(meme);

        List<MemeAsset> saved = new ArrayList<>(2);
        saved.add(markReady(meme.getId(), MemeAssetKind.VIDEO, publication.videoKey(),
                publication.videoContentType(), publication.videoSize(), now));
        if (publication.posterKey() != null) {
            saved.add(markReady(meme.getId(), MemeAssetKind.POSTER, publication.posterKey(),
                    publication.posterContentType(), publication.posterSize(), now));
        } else {
            // Заставку убрали — строка файла обязана уйти вместе с ней,
            // иначе карточка будет ссылаться на объект, которого нет.
            assets.findById(assetId(meme.getId(), MemeAssetKind.POSTER)).ifPresent(assets::delete);
        }
        return card(meme, saved, owners(List.of(meme)));
    }

    /**
     * Снять мем с публикации.
     *
     * <p>Карточка остаётся надгробием, а строки файлов уходят: сами объекты
     * удаляет сценарий, и держать ключ удалённого объекта значило бы занимать
     * им уникальный индекс до скончания века.
     *
     * <p>Исключение — черновик. Надгробие ставится тому, что было в
     * библиотеке, а черновик там не был ни секунды: это выданный билет на
     * загрузку, у которого ещё нет ни названия, ни длительности. Ограничение
     * {@code ck_meme_card} разрешает пустое название только черновику, поэтому
     * попытка перевести его в {@code withdrawn} падала нарушением ограничения
     * — наружу это выходило ответом 409 «Данные изменились: повторите
     * попытку», и повтор не помогал никогда: брошенный билет нельзя было
     * убрать вовсе. Черновик поэтому удаляется целиком, вместе со своим
     * незалитым файлом.
     *
     * @return ключи объектов, которые теперь надо убрать из хранилища
     */
    @Transactional
    public List<String> withdraw(String memeId, Instant now) {
        Meme meme = memes.findBySlug(memeId).orElse(null);
        if (meme == null) {
            return List.of();
        }
        List<MemeAsset> files = assets.findByMemeId(meme.getId());
        List<String> keys = new ArrayList<>(files.size());
        for (MemeAsset asset : files) {
            keys.add(asset.getStorageKey());
        }
        assets.deleteAll(files);
        if (Meme.DRAFT.equals(meme.getStatus())) {
            memes.delete(meme);
            return keys;
        }
        meme.setStatus(Meme.WITHDRAWN);
        meme.setWithdrawnAt(now);
        memes.save(meme);
        return keys;
    }

    /**
     * Записать сжатую для мобильных версию ролика.
     *
     * <p>Байты кладёт в хранилище сценарий, здесь остаётся отметка о версии и
     * новое состояние файла. Раньше сжатый ролик лежал текстом прямо в строке
     * карточки, и чтение библиотеки тянуло эти мегабайты в память на каждую
     * карточку.
     */
    @Transactional
    public Card recordOptimization(String memeId, String version, String contentType,
                                   long sizeBytes, Integer durationMs, Instant now) {
        Meme meme = memes.findBySlug(memeId)
                .orElseThrow(() -> ApiException.of("MEME_NOT_FOUND", 404));
        meme.setOptimizedVersion(version);
        meme.setOptimizedAt(now);
        if (durationMs != null) {
            meme.setDurationMs(durationMs);
        }
        memes.save(meme);
        // Ролик тот же по ключу, но другой по содержимому: номер редакции
        // растёт, и по нему клиент сбрасывает кеш подписанных ссылок.
        assets.findById(assetId(meme.getId(), MemeAssetKind.VIDEO)).ifPresent(video -> {
            video.setContentType(contentType);
            video.setSizeBytes(sizeBytes);
            video.setRevision(video.getRevision() + 1);
            assets.save(video);
        });
        return card(meme, assets.findByMemeId(meme.getId()), owners(List.of(meme)));
    }

    /**
     * Сверка с бакетом: у объекта есть карточка или её надо завести.
     *
     * <p>Владелец читается из самого пути — ровно так же, как его туда положил
     * сервер, выдавая билет. Поэтому у восстановленного мема автор есть, а не
     * значится «S3», как в прежней сверке.
     */
    @Transactional
    public Recovery reconcile(Discovered found, Instant now) {
        Meme meme = memes.findBySlug(found.memeId()).orElse(null);
        boolean fresh = meme == null;
        if (fresh) {
            meme = newCard(found.memeId(), found.ownerUid(), found.divisionLanguage());
            meme.setOrigin(Meme.RECONCILED);
            meme.setTitle(found.title());
            meme.setDurationMs(found.durationMs());
            meme.setStatus(Meme.ACTIVE);
            meme.setPublishedAt(now);
            meme.setCreatedAt(found.videoModifiedAt() == null ? now : found.videoModifiedAt());
            memes.save(meme);
        }
        boolean hadVideo = assets.findById(assetId(meme.getId(), MemeAssetKind.VIDEO)).isPresent();
        if (fresh || !hadVideo) {
            markReady(meme.getId(), MemeAssetKind.VIDEO, found.videoKey(),
                    found.videoContentType(), found.videoSize(), now);
            if (found.posterKey() != null) {
                markReady(meme.getId(), MemeAssetKind.POSTER, found.posterKey(),
                        found.posterContentType(), found.posterSize(), now);
            }
            return fresh ? Recovery.RECOVERED : Recovery.COMPLETED;
        }
        return Recovery.INTACT;
    }

    /** Что сделала сверка с одним найденным объектом. */
    public enum Recovery {
        /** Карточки не было — завели. */
        RECOVERED,
        /** Карточка была, но не знала про файл — дополнили. */
        COMPLETED,
        /** Карточка и файл на месте — не трогали. */
        INTACT
    }

    // ───────────────────────────── внутреннее ─────────────────────────────

    private Meme newCard(String memeId, String ownerUid, String divisionLanguage) {
        boolean builtin = ownerUid == null || ownerUid.isBlank();
        // Без обеих отметок обратный перевод не найдёт ни мем, ни владельца:
        // md5 не обращается, и именно ради этого мост держит таблицу.
        ids.rememberMemes(List.of(memeId));
        if (!builtin) {
            ids.rememberPlayers(List.of(ownerUid));
        }
        return Meme.builder()
                .id(ids.memeId(memeId))
                .slug(memeId)
                .divisionLanguage(divisionLanguage)
                .origin(builtin ? Meme.BUILTIN : Meme.PLAYER)
                .ownerPlayerId(builtin ? null : ids.playerId(ownerUid))
                .status(Meme.DRAFT)
                .build();
    }

    private MemeAsset markReady(UUID memeId, MemeAssetKind kind, String storageKey,
                                String contentType, long sizeBytes, Instant now) {
        MemeAsset asset = assets.findById(assetId(memeId, kind)).orElseGet(() -> MemeAsset.builder()
                .memeId(memeId)
                .kind(kindOf(kind))
                .build());
        boolean changed = !storageKey.equals(asset.getStorageKey())
                || asset.getSizeBytes() == null || asset.getSizeBytes() != sizeBytes;
        asset.setStorageKey(storageKey);
        asset.setContentType(contentType);
        asset.setSizeBytes(Math.max(0, sizeBytes));
        asset.setState(MemeAsset.READY);
        asset.setUploadExpiresAt(null);
        asset.setUploadedAt(now);
        if (changed) {
            asset.setRevision(asset.getRevision() + 1);
        }
        return assets.save(asset);
    }

    private Map<UUID, String> owners(Collection<Meme> rows) {
        List<UUID> playerIds = new ArrayList<>(rows.size());
        for (Meme meme : rows) {
            if (meme.getOwnerPlayerId() != null) {
                playerIds.add(meme.getOwnerPlayerId());
            }
        }
        return playerIds.isEmpty() ? Map.of() : ids.playerUids(playerIds);
    }

    private static Card card(Meme meme, Collection<MemeAsset> files, Map<UUID, String> owners) {
        StoredFile video = null;
        StoredFile poster = null;
        for (MemeAsset asset : files) {
            StoredFile file = new StoredFile(asset.getStorageKey(), asset.getContentType(),
                    asset.getSizeBytes() == null ? 0L : asset.getSizeBytes(),
                    MemeAsset.READY.equals(asset.getState()), asset.getRevision());
            if (MemeAsset.VIDEO.equals(asset.getKind())) {
                video = file;
            } else {
                poster = file;
            }
        }
        return new Card(
                meme.getSlug(),
                meme.getTitle(),
                meme.getDurationMs() == null ? 0 : meme.getDurationMs(),
                meme.getOwnerPlayerId() == null ? null : owners.get(meme.getOwnerPlayerId()),
                meme.getDivisionLanguage(),
                Meme.BUILTIN.equals(meme.getOrigin()),
                meme.getStatus(),
                meme.getSourceUrl(),
                meme.getImportMode(),
                meme.getCreatedAt(),
                video,
                poster);
    }

    private static MemeAssetId assetId(UUID memeId, MemeAssetKind kind) {
        return new MemeAssetId(memeId, kindOf(kind));
    }

    private static String kindOf(MemeAssetKind kind) {
        return kind == MemeAssetKind.POSTER ? MemeAsset.POSTER : MemeAsset.VIDEO;
    }

    private static List<String> distinct(Collection<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    /** Файл мема в объёме, который нужен карточке. */
    public record StoredFile(String storageKey, String contentType, long sizeBytes, boolean ready, int revision) {
    }

    /** Карточка мема так, как её видит область; сущность наружу не выходит. */
    public record Card(String memeId, String title, int durationMs, String ownerUid,
                       String divisionLanguage, boolean builtin, String status,
                       String sourceUrl, String importMode, Instant createdAt,
                       StoredFile video, StoredFile poster) {

        public boolean active() {
            return ACTIVE.equals(status);
        }
    }

    /** Заявка на публикацию: файлы уже в хранилище, их размеры сверены с ним. */
    public record Publication(String memeId, String ownerUid, String title, Integer durationMs,
                              String divisionLanguage, String sourceUrl, String importMode,
                              String videoKey, String videoContentType, long videoSize,
                              String posterKey, String posterContentType, long posterSize) {
    }

    /** Объект бакета, у которого нашлась (или не нашлась) карточка. */
    public record Discovered(String memeId, String ownerUid, String divisionLanguage,
                             String title, int durationMs,
                             String videoKey, String videoContentType, long videoSize, Instant videoModifiedAt,
                             String posterKey, String posterContentType, long posterSize) {
    }
}
