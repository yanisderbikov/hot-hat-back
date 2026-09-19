package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceInviteView;
import ru.hothat.conference.api.dto.ConferenceInvitesResponseDTO;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Свои приглашения в видео-чаты, ждущие ответа.
 *
 * <p>Тот же сценарий кормит поле {@code conferenceInvites} канала
 * {@code /ws/v2/me/social}: карточка «Вступить / Отклонить» висит на любой
 * странице портала, пока человек не ответит или видео-чат не истечёт.
 * Истёкшие отфильтровываются на чтении — сторожа, гасящего их строки, нет.
 */
@Service
@RequiredArgsConstructor
public class ListMyConferenceInvitesUseCase {

    /** Больше карточек на экране не поместится, да и не бывает. */
    private static final int LIMIT = 20;

    private final ConferenceStore store;
    private final PlayerCardPort cards;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public ConferenceInvitesResponseDTO run(HotHatUser user) {
        long now = clock.millis();
        List<ConferenceStore.InviteRow> alive = new ArrayList<>();
        for (ConferenceStore.InviteRow row : store.pendingInvites(user.uid())) {
            if (!ConferenceRules.expired(row.conferenceClosed(), row.expiresAtMs(), now)) {
                alive.add(row);
            }
            if (alive.size() >= LIMIT) {
                break;
            }
        }
        Set<String> inviters = new LinkedHashSet<>();
        alive.forEach(row -> inviters.add(row.inviterUid()));
        Map<String, PlayerCardPort.Card> known = cards.cards(inviters);
        List<ConferenceInviteView> items = new ArrayList<>(alive.size());
        for (ConferenceStore.InviteRow row : alive) {
            PlayerCardPort.Card inviter = known.get(row.inviterUid());
            items.add(new ConferenceInviteView(
                    row.conferenceId(),
                    row.inviterUid(),
                    inviter == null ? cards.nicknameOf(row.inviterUid()) : inviter.nickname(),
                    inviter == null || inviter.avatarDataUrl() == null || inviter.avatarDataUrl().isBlank()
                            ? null : inviter.avatarDataUrl(),
                    row.createdAtMs(),
                    row.expiresAtMs()));
        }
        return new ConferenceInvitesResponseDTO(items);
    }
}
