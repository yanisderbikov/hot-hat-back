package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.MyProfileResponseDTO;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.team.spi.RankedTeamPort;

/**
 * Показать карточку текущего игрока.
 *
 * <p>Карточка заводится при первом обращении, если её ещё нет: так попадают на
 * экран учётки, заведённые до переезда области. Поэтому сценарий читающий, но
 * пишущий — разделять его на «прочитать» и «завести» нельзя, иначе игрок без
 * карточки увидел бы пустой экран вместо своего имени.
 *
 * <p>Дивизион при этом <b>не</b> закрепляется. Раньше то же самое чтение
 * ставило {@code division_locked_at}, и новичок оказывался навсегда в русском
 * дивизионе, не выбрав ничего: экран онбординга открывался уже запертым.
 *
 * <p>Транзакция без {@code readOnly} и объявлена здесь: заведение карточки и
 * счёт обоймы должны видеть одно состояние.
 */
@Service
@RequiredArgsConstructor
public class GetMyProfileUseCase {

    private final ProfileStore profiles;
    /** Счётчик обоймы спрашивается у её владельца — области диверсий. */
    private final SabotageArmoryPort armory;
    private final ProfileViews views;
    /** «Есть ли у меня команда» знает её область, а не колонка в карточке. */
    private final RankedTeamPort rankedTeams;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public MyProfileResponseDTO run(HotHatUser user) {
        ProfileStore.Card card = profiles.ensureCard(user.uid(), user.emailLower(), null);
        return new MyProfileResponseDTO(
                views.profileCard(card, rankedTeams.teamOf(user.uid()).isPresent()),
                views.memeLoadout(armory.loadout(user.uid())));
    }
}
