package ru.hothat.media.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Карточки мемов в таблице {@code v2.meme}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link MemeStore}.
 *
 * <p>Читают по {@code slug}, а не по ключу: слог — это тот самый
 * {@code meme-9f31ab77c204}, который стоит в пути объекта и приезжает от
 * клиента. Ключ строки суррогатный ровно затем, чтобы браузер её не называл.
 *
 * <p>Витрина отбирается по статусу с сортировкой по времени — тем же
 * порядком, каким её обслуживает частичный индекс {@code ix_meme_catalog}.
 * Раньше предел применялся к строкам, половину которых потом выбрасывал
 * отбор в памяти, и страница молча оказывалась неполной.
 */
@Repository
interface Memes extends JpaRepository<Meme, UUID> {

    Optional<Meme> findBySlug(String slug);

    List<Meme> findByStatusOrderByCreatedAtDesc(String status, Limit limit);

    /** Какие из названных мемов существуют: обойма спрашивает про пять сразу. */
    List<Meme> findBySlugIn(Collection<String> slugs);
}
