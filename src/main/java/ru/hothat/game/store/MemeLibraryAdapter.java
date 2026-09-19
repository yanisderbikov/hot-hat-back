package ru.hothat.game.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.HotHatProperties;
import ru.hothat.game.domain.weapon.MemeMedia;
import ru.hothat.game.port.MemeCatalogPort;
import ru.hothat.media.spi.MemeLibraryPort;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Переходник к библиотеке мемов.
 *
 * <p>В таблицу больше не ходит: карточка мема принадлежит области медиа, и
 * читается она через её порт. Раньше здесь лежал свой репозиторий поверх
 * {@code meme_library} — партия знала и про поля файла, и про то, что ролик
 * бывает записан прямо в строке.
 *
 * <p>Что осталось здесь по праву — знание сцены о встроенном ролике: он лежит
 * в статике сайта, и его адрес собирается из настроек фронтенда, а не из
 * хранилища. Карточка у встроенного мема в базе есть (её сеет миграция), но
 * файла в бакете нет и не будет.
 */
@Component
@RequiredArgsConstructor
public class MemeLibraryAdapter implements MemeCatalogPort {

    /** Встроенные ролики: идентификатор → длительность. */
    private static final Map<String, Integer> BUILTIN = Map.of("builtin-bmw-drugoy-ne-znayu", 5000);
    private static final String BUILTIN_BMW = "builtin-bmw-drugoy-ne-znayu";

    private final MemeLibraryPort library;
    private final HotHatProperties properties;

    @Override
    public Optional<MemeMedia> find(String memeId) {
        if (memeId == null || memeId.isBlank()) {
            return Optional.empty();
        }
        MemeLibraryPort.PlayableMeme meme = library.find(memeId).orElse(null);
        Integer builtin = BUILTIN.get(memeId);
        if (meme == null && builtin == null) {
            return Optional.empty();
        }
        long duration = meme != null && meme.durationMs() > 0
                ? meme.durationMs() : (builtin == null ? 5000 : builtin);
        return Optional.of(new MemeMedia(memeId,
                title(meme, memeId),
                duration,
                firstNonBlank(meme == null ? null : meme.videoUrl(), builtinSrc(memeId)),
                firstNonBlank(meme == null ? null : meme.posterUrl(), builtinPoster(memeId)),
                meme == null || meme.videoKey() == null ? "" : meme.videoKey(),
                meme == null || meme.posterKey() == null ? "" : meme.posterKey(),
                // Провайдер хранилища у всех живых мемов один: строки, чей
                // ролик лежал байтами в базе, файла не имеют вовсе.
                meme == null || meme.videoKey() == null ? "" : STORAGE_PROVIDER));
    }

    /** Тем же значением помечались строки с файлом в S3; клиент читает его как есть. */
    private static final String STORAGE_PROVIDER = "s3-compatible";

    @Override
    public boolean exists(String memeId) {
        return memeId != null && (BUILTIN.containsKey(memeId) || library.find(memeId).isPresent());
    }

    @Override
    public Set<String> existing(Collection<String> memeIds) {
        Set<String> found = new LinkedHashSet<>();
        Set<String> ask = new LinkedHashSet<>();
        for (String memeId : memeIds) {
            if (memeId == null || memeId.isBlank()) {
                continue;
            }
            // Встроенный ролик лежит в статике сайта, и сцена покажет его
            // даже без карточки: спрашивать про него библиотеку нечего.
            if (BUILTIN.containsKey(memeId)) {
                found.add(memeId);
            } else {
                ask.add(memeId);
            }
        }
        if (!ask.isEmpty()) {
            // Один запрос на всю обойму: перебор идёт по ответу выборки.
            found.addAll(library.existing(ask));
        }
        return found;
    }

    private static String title(MemeLibraryPort.PlayableMeme meme, String memeId) {
        if (meme != null && meme.title() != null && !meme.title().isBlank()) {
            return meme.title();
        }
        return BUILTIN_BMW.equals(memeId) ? "BMW — другой не знаю" : "Мем";
    }

    private String builtinSrc(String memeId) {
        return BUILTIN_BMW.equals(memeId) ? properties.publicSiteUrl() + "/assets/memes/bmw-drugoy-ne-znayu.mp4" : "";
    }

    private String builtinPoster(String memeId) {
        return BUILTIN_BMW.equals(memeId) ? properties.publicSiteUrl() + "/assets/memes/bmw-drugoy-ne-znayu.webp" : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
