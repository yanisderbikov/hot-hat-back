package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.admin.api.dto.BanUserRequestDTO;
import ru.hothat.admin.api.dto.BannedUserResponseDTO;
import ru.hothat.admin.store.ModerationStore;
import ru.hothat.auth.spi.AccountPort;
import ru.hothat.auth.spi.IdentityCommandPort;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Заблокировать игрока.
 *
 * <p>Заменяет {@code POST /api/admin} с {@code action=ban_user}.
 *
 * <p>Одна транзакция на четыре записи: строка блокировки, след решения, отзыв
 * доступа у области личности и, если названа комната, пометка места игрока.
 * Разрывать её событиями запрещено (§7.3, строка «Бан игрока»): аккаунт,
 * помеченный забаненным, но с живым refresh-токеном — это человек, который
 * вернётся в игру через пятнадцать минут.
 *
 * <p>Отзыв доступа делает не этот сценарий: {@code token_version} и таблица
 * refresh-токенов принадлежат области {@code auth}, и она их и пишет — здесь
 * только просьба через {@link IdentityCommandPort}. Раньше та же операция
 * лезла в чужую строку сама, вдобавок читая её перед записью: одновременные
 * бан и смена пароля теряли один инкремент, то есть отозванная сессия
 * оставалась живой.
 *
 * <p>Источников правды о блокировке теперь два и они не пересекаются: факт —
 * строка {@code v2.user_ban}, мгновенный отзыв доступа — поколение токенов.
 * Флага {@code banned} в учётке больше нет; расходиться нечему.
 *
 * <p>Единственное, что уходит за коммит, — выставление из видеокомнаты: это
 * внешний вызов, и внутри транзакции ему не место.
 */
@Service
@RequiredArgsConstructor
public class BanUserUseCase {

    private static final String DEFAULT_REASON = "Заблокирован администратором";

    private final ModerationStore moderation;
    private final AccountPort accounts;
    private final IdentityCommandPort identities;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final ApplicationEventPublisher events;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BannedUserResponseDTO run(HotHatUser admin, BanUserRequestDTO request) {
        // Самобан выглядит как опечатка в чужом uid, но стоит администратору
        // доступа в собственную панель: своё поколение токенов он поднимет сам.
        if (request.uid().equals(admin.uid())) {
            throw ErrorCode.BAN_SELF_FORBIDDEN.raise();
        }
        if (accounts.account(request.uid()).isEmpty()) {
            throw ErrorCode.BAN_TARGET_NOT_FOUND.raise();
        }

        String reason = Json.str(request.reason() == null ? DEFAULT_REASON : request.reason(), 300);
        ModerationStore.Ban ban = moderation.ban(request.uid(), reason, admin.uid());
        identities.revokeAccess(request.uid(), IdentityCommandPort.REASON_BAN);

        boolean seatRevoked = revokeRoomSeat(request.roomId(), request.uid());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("roomId", request.roomId());
        details.put("roomSeatRevoked", seatRevoked);
        moderation.record(admin.uid(), ModerationStore.ACTION_BAN,
                ModerationStore.SUBJECT_PLAYER, request.uid(), details);

        if (request.roomId() != null && !request.roomId().isBlank()) {
            // Выставляем из видеокомнаты, даже если строки места не нашлось:
            // в LiveKit человек мог остаться, а место уже удалили. Ответ сети
            // ждём после коммита — внутри транзакции ему не место.
            events.publishEvent(new AdminEvictionEvents.PlayerBanned(request.roomId(), request.uid()));
        }
        return new BannedUserResponseDTO(ban.uid(), ban.reason(), ban.byUid(),
                ban.bannedAtMs(), seatRevoked, true);
    }

    /**
     * Помечает место игрока в названной комнате. Комната необязательна: бан
     * существует сам по себе, а из комнаты игрок мог уже выйти.
     */
    private boolean revokeRoomSeat(String roomId, String uid) {
        if (roomId == null || roomId.isBlank()) {
            return false;
        }
        Optional<RoomPlayer> seat = getterRoom.getPlayer(roomId, uid);
        if (seat.isEmpty()) {
            return false;
        }
        RoomPlayer player = seat.get();
        // Ноль в последнем появлении выводит игрока из активных немедленно:
        // иначе он ещё четыре минуты числился бы онлайн в чужих списках.
        player.setLastSeenAt(0L);
        player.setBannedAt(Instant.now());
        saverRoom.savePlayer(player);
        return true;
    }
}
