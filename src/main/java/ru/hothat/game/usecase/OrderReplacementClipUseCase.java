package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ReplacementClipResponseDTO;
import ru.hothat.game.api.dto.OrderReplacementClipRequestDTO;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.domain.weapon.ReplacementClipRules;
import ru.hothat.game.domain.weapon.SabotageEngine;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Заказать съёмку клипа Подмены.
 *
 * <p>Съёмка — не выстрел: заряд тратится при применении клипа, а слот занимает
 * плёнка. Поэтому и проверки здесь другие, и заказать съёмку можно с пустым
 * боезапасом — в расчёте на награду к моменту показа.
 *
 * <p>Снимать разрешено не позднее, чем за одиннадцать секунд до конца хода:
 * клип длится десять, и снятый на последней секунде оказался бы обрывком.
 */
@Service
@RequiredArgsConstructor
public class OrderReplacementClipUseCase {

    private final MatchStore matchStore;
    private final SabotageEngine sabotage;
    private final MatchViewAssembler views;
    private final ApplicationEventPublisher events;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public ReplacementClipResponseDTO run(HotHatUser user, String roomId,
                                          OrderReplacementClipRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        MatchPlayer attacker = session.player(user.uid());
        SabotageEvent event = sabotage.orderClip(session.state(), attacker, request.targetUid());
        session.commit();
        events.publishEvent(new MatchEvents.SabotageApplied(roomId, event));
        int used = ReplacementClipRules.ownedBy(session.state(), user.uid());
        return new ReplacementClipResponseDTO(
                views.clip(session.state().getClips().get(event.clipId())),
                views.event(event),
                ReplacementClipRules.RECORD_EVENT_MS,
                Math.max(0, ReplacementClipRules.SLOTS - used));
    }
}
