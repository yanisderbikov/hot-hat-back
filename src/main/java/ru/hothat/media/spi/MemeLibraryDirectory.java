package ru.hothat.media.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.media.store.MemeStore;
import ru.hothat.media.usecase.MemeFileUrls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Реализация {@link MemeLibraryPort}: живёт у владельца таблиц. */
@Component
@RequiredArgsConstructor
public class MemeLibraryDirectory implements MemeLibraryPort {

    private final MemeStore memes;
    private final MemeFileUrls urls;

    @Override
    public Optional<PlayableMeme> find(String memeId) {
        return memes.find(memeId).filter(MemeStore.Card::active).map(this::view);
    }

    @Override
    public Set<String> existing(Collection<String> memeIds) {
        return memes.existing(memeIds);
    }

    @Override
    public List<PlayableMeme> catalog(int limit) {
        List<PlayableMeme> result = new ArrayList<>();
        for (MemeStore.Card card : memes.catalog(limit)) {
            result.add(view(card));
        }
        return result;
    }

    private PlayableMeme view(MemeStore.Card meme) {
        MemeStore.StoredFile video = meme.video();
        MemeStore.StoredFile poster = meme.poster();
        return new PlayableMeme(
                meme.memeId(),
                meme.title(),
                meme.durationMs(),
                video == null ? null : urls.stable(video.storageKey()),
                video == null ? null : video.storageKey(),
                poster == null ? null : urls.stable(poster.storageKey()),
                poster == null ? null : poster.storageKey());
    }
}
