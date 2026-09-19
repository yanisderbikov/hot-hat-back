package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.CompleteOnboardingRequestDTO;
import ru.hothat.profile.api.dto.CompletedOnboardingResponseDTO;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.room.spi.RoomNicknamePort;
import ru.hothat.team.spi.RankedTeamPort;

/**
 * Первый вход: дивизион, ник, аватар и согласия — одной транзакцией.
 *
 * <p>Сегодня браузер делает это четырьмя запросами подряд, и порядок важен:
 * дивизион закрепляется навсегда первым же шагом. Обрыв связи после него
 * оставлял учётную запись с вечным дивизионом и без ника, а повторная попытка
 * с другим языком натыкалась на {@code DIVISION_LOCKED} — выбраться из этого
 * состояния игрок сам не мог. Здесь шаги идут в той же последовательности,
 * но откатываются вместе.
 *
 * <p>Карточка заводится, если её ещё нет: онбординг может прийти от гостя,
 * который завёлся до появления карточек.
 */
@Service
@RequiredArgsConstructor
public class CompleteOnboardingUseCase {

    private final ProfileStore profiles;
    private final ProfileViews views;
    private final RoomNicknamePort roomNicknames;
    /** Команды у новичка быть не может, но спрашиваем её область: одно правило. */
    private final RankedTeamPort rankedTeams;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public CompletedOnboardingResponseDTO run(HotHatUser user, CompleteOnboardingRequestDTO request) {
        profiles.ensureCard(user.uid(), request.nickname(), request.divisionLanguage().wireValue());
        profiles.lockDivision(user.uid(), request.divisionLanguage().wireValue());
        String nickname = profiles.rename(user.uid(), request.nickname());
        roomNicknames.renameEverywhere(user.uid(), nickname);
        if (request.avatarDataUrl() != null && !request.avatarDataUrl().isBlank()) {
            profiles.replaceAvatar(user.uid(), request.avatarDataUrl());
        }
        profiles.recordConsents(user.uid(), views.versionsOf(request.versions()),
                Boolean.TRUE.equals(request.adultConfirmed()), null, null);
        return new CompletedOnboardingResponseDTO(views.profileCard(
                profiles.card(user.uid()).orElseThrow(),
                rankedTeams.teamOf(user.uid()).isPresent()));
    }
}
