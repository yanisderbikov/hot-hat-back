package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.spi.ChatCommandPort;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.social.RoomInvite;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.GetterSocial;
import ru.hothat.repository.SaverSocial;
import ru.hothat.room.api.dto.AcceptedRoomInviteResponseDTO;
import ru.hothat.room.api.dto.RoomInviteState;
import ru.hothat.room.domain.RoomAccessPolicy;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomPresence;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Divisions;

import java.time.Instant;
import java.util.List;

/**
 * Принять приглашение и сесть в комнату.
 *
 * <p>Принятие и вход — одно действие, а не два. Раньше их было два: движок
 * заводил место, а браузер следом читал комнату и решал, пускать ли себя
 * ({@code app-core.js:4818}). Между ними умещался старт партии, и человек
 * оказывался с местом в комнате, куда его уже не впустят.
 *
 * <p>Допуск здесь тот же, что у входа по идентификатору: приглашение отвечает
 * на вопрос «звали ли меня», а не «можно ли мне сюда». Раньше эти вопросы
 * различались: приглашённый садился мимо проверки дивизиона и упирался в отказ
 * только при выдаче видеотокена — то есть сидел в комнате без видео и без
 * объяснения.
 *
 * <p>Право адресата — инвариант сценария: чужое приглашение и несуществующее
 * отвечают по-разному только потому, что первое требует чтения самой строки.
 */
@Service
@RequiredArgsConstructor
public class AcceptRoomInviteUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final GetterSocial getterSocial;
    private final SaverSocial saverSocial;
    /** Обойма мемов принадлежит области диверсий, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    /** Имя, аватар и дивизион — у карточки игрока, а не в оболочке учётки. */
    private final PlayerCardPort cards;
    /** Значок входящих принадлежит переписке; комната только просит его погасить. */
    private final ChatCommandPort chat;
    private final RoomInvites roomInvites;
    private final RoomSeats roomSeats;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public AcceptedRoomInviteResponseDTO run(HotHatUser user, String inviteId) {
        RoomInvite invite = getterSocial.getRoomInvite(inviteId)
                .orElseThrow(() -> ApiException.of("ROOM_INVITE_NOT_FOUND", 404));
        if (!user.uid().equals(invite.getToUid())) {
            throw ApiException.of("ROOM_INVITE_FORBIDDEN", 403);
        }

        Room room = roomAuthz.requireRoomForWrite(invite.getRoomId());
        long now = System.currentTimeMillis();
        RoomInviteState state = roomInvites.stateOf(user.uid(), invite, room, now);
        if (!state.available()) {
            throw refusal(state);
        }

        List<String> loadout = armory.requireLoadout(user.uid());
        PlayerCardPort.Card card = cards.card(user.uid()).orElse(null);
        refuseByRoomRules(room, card, user.uid(), now);

        RoomSeats.Seated seated = roomSeats.seatPlayer(room, user.uid(), card, loadout,
                RoomSeats.Origin.invite(invite.getFromUid(), invite.getId()));

        invite.setStatus("accepted");
        invite.setAcceptedAt(Instant.now());
        saverSocial.saveRoomInvite(invite);
        // Строка входящих принадлежит переписке, а не комнате: гасит карточку
        // её владелец. Иначе она осталась бы вечно «ждёт ответа», а у значка
        // оказалось бы два писателя из разных областей.
        chat.markRoomInviteAccepted(user.uid(), invite.getId());

        return new AcceptedRoomInviteResponseDTO(
                roomInvites.describe(user.uid(), invite, room, loadout.size(), now),
                projections.room(room),
                projections.seat(seated.player(), now));
    }

    /**
     * Отказ по состоянию приглашения.
     *
     * <p>Три разных кода, потому что человеку нужно разное: истёкшее просят
     * попросить заново, начавшуюся игру — подождать следующей, отозванное не
     * объясняют вовсе.
     */
    private static ApiException refusal(RoomInviteState state) {
        return switch (state) {
            case EXPIRED -> ApiException.of("ROOM_INVITE_EXPIRED", 410);
            case GAME_STARTED -> ApiException.of("ROOM_INVITE_GAME_STARTED", 409);
            default -> ApiException.of("ROOM_INVITE_UNAVAILABLE", 409);
        };
    }

    /** Тот же допуск, что у входа по идентификатору: приглашение его не отменяет. */
    private void refuseByRoomRules(Room room, PlayerCardPort.Card card, String uid, long nowMs) {
        boolean seatExists = getterRoom.getPlayer(room.getId(), uid).isPresent();
        int othersAlive = (int) getterRoom.getPlayers(room.getId()).stream()
                .filter(player -> !player.getUid().equals(uid))
                .filter(player -> RoomPresence.playerAlive(
                        Boolean.TRUE.equals(player.getIsTestBot()),
                        player.getLastSeenAt() == null ? 0L : player.getLastSeenAt(), nowMs))
                .count();
        RoomAccessPolicy.RoomFacts facts = new RoomAccessPolicy.RoomFacts(
                RoomPhase.fromWire(room.getPhase()),
                room.isClosed(),
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getManagedMatchmaking()),
                Divisions.normalize(room.getDivisionLanguage()),
                Divisions.normalize(RoomProjections.gameLanguage(room)),
                room.effectiveMaxPlayers());
        RoomAccessPolicy.Applicant applicant = new RoomAccessPolicy.Applicant(
                Divisions.normalize(card == null ? null : card.divisionLanguage()), seatExists, false, othersAlive);
        RoomAccessPolicy.refuseSeat(facts, applicant).ifPresent(refused -> {
            throw RoomRefusals.of(refused);
        });
    }
}
