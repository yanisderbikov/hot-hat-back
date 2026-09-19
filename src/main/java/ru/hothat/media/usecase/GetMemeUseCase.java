package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.MemeCardResponseDTO;
import ru.hothat.media.store.MemeStore;

/**
 * Показать один мем.
 *
 * <p>Нужен догрузке мимо кеша: диверсию выпустили мемом, которого нет в
 * загруженной библиотеке, и до выстрела остаются секунды
 * ({@code app-core.js:5184}).
 *
 * <p>Снятый мем отвечает 404, а не 403: для игрока его больше нет, и
 * различать «нет такого» и «есть, но вам нельзя» здесь нечем и незачем.
 * Черновик отвечает так же — карточки у него ещё нет, есть только билет.
 */
@Service
@RequiredArgsConstructor
public class GetMemeUseCase {

    private final MemeStore memes;
    private final MemeCardMapper cards;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public MemeCardResponseDTO run(HotHatUser user, String memeId) {
        MemeStore.Card meme = memes.find(memeId)
                .filter(MemeStore.Card::active)
                .orElseThrow(() -> ApiException.of("MEME_NOT_FOUND", 404));
        return new MemeCardResponseDTO(cards.card(meme, user.uid()));
    }
}
