package ru.hothat.media.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Файлы мемов в таблице {@code v2.meme_asset}.
 *
 * <p>Не публичный по той же причине, что и {@link Memes}.
 *
 * <p>Чтение по списку мемов, а не по одному: страница библиотеки — шестьдесят
 * карточек, и у каждой ролик с заставкой. Спрашивать про них поштучно значило
 * бы сто двадцать чтений на одну страницу.
 */
@Repository
interface MemeAssets extends JpaRepository<MemeAsset, MemeAssetId> {

    List<MemeAsset> findByMemeIdIn(Collection<UUID> memeIds);

    List<MemeAsset> findByMemeId(UUID memeId);

    /** Кому принадлежит объект хранилища: сверка бакета с библиотекой. */
    Optional<MemeAsset> findByStorageKey(String storageKey);

    /** Просроченные билеты: объект не залили, черновик пора убрать. */
    List<MemeAsset> findByStateAndUploadExpiresAtBefore(String state, Instant before);
}
