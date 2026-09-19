package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.SaveDefaultLoadoutRequestDTO;
import ru.hothat.profile.api.dto.SavedDefaultLoadoutResponseDTO;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

import java.util.List;

/**
 * Задать обойму мемов учётной записи.
 *
 * <p>Заменяет прямую запись браузера в {@code users/{uid}.defaultMemeLoadout}
 * — тот же адрес, что и раньше, но за ним теперь пять строк слотов, а не
 * массив в карточке. Разница видна на двух вещах, которые массив допускал:
 * один мем мог стоять в обойме дважды (четыре заряда вместо пяти), а «в
 * скольких обоймах стоит этот мем» массив не отвечал вовсе — при снятии мема
 * с публикации приходилось перебирать всех игроков.
 *
 * <p>Трёх полей отметок времени больше нет. {@code defaultMemeLoadoutUpdatedAt}
 * стал колонкой слота, а {@code defaultMemeLoadoutConfirmedAt} исчез как
 * понятие: «обойма собрана» — это её размер, и хранить рядом со списком его
 * же признак значило заводить счётчик, который однажды разойдётся со списком.
 * Готовность считается при чтении и едет в {@code status}.
 *
 * <p>Нормализация — та же, которой обойму читают: повтор мема не отказ, а
 * схлопывание. Экран арсенала сохраняет выбор на каждом щелчке, и на
 * «черновой» обойме отказывать не за что; важно лишь, чтобы записанное
 * совпадало с тем, что вернёт чтение, — поэтому в ответе едет сохранённое.
 */
@Service
@RequiredArgsConstructor
public class SaveDefaultLoadoutUseCase {

    private final SabotageArmoryPort armory;
    private final ProfileViews mapper;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SavedDefaultLoadoutResponseDTO run(HotHatUser user, SaveDefaultLoadoutRequestDTO request) {
        List<String> loadout = armory.saveLoadout(user.uid(), request.memeIds());
        return new SavedDefaultLoadoutResponseDTO(loadout, mapper.memeLoadout(loadout));
    }
}
