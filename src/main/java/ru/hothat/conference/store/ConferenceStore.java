package ru.hothat.conference.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.domain.ConferenceRules.MemberStatus;
import ru.hothat.util.Ids;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области видео-чата в свои таблицы.
 *
 * <p>Наружу отдаёт строки и маленькие записи, а не сущности: три класса
 * {@code VideoConference*} не публичны и в сигнатуры сценариев не попадают.
 * Здесь же живёт перевод uid ↔ uuid, поэтому сценарии говорят
 * идентификаторами, которые понимает фронтенд.
 *
 * <p>Ни один метод не читает в цикле: состав видео-чата — один запрос за
 * строками и один за обратным переводом, независимо от числа участников.
 */
@Component
@RequiredArgsConstructor
public class ConferenceStore {

    /** Состояния паспорта; набор закрыт ограничением базы. */
    public static final String OPEN = "open";
    public static final String CLOSED = "closed";

    private final VideoConferences conferences;
    private final VideoConferenceMembers members;
    private final VideoConferenceMessages messages;
    private final LegacyIdBridge ids;

    /**
     * Завести видео-чат; хозяин сразу участник.
     *
     * @return идентификатор нового видео-чата
     */
    public String create(String hostUid, Instant now) {
        ids.rememberPlayers(List.of(hostUid));
        String id = "vc-" + Ids.hex(8);
        conferences.save(VideoConference.builder()
                .id(id)
                .host(ids.playerId(hostUid))
                .status(OPEN)
                .createdAt(now)
                .expiresAt(now.plus(ConferenceRules.TTL))
                .build());
        members.save(VideoConferenceMember.builder()
                .conferenceId(id)
                .player(ids.playerId(hostUid))
                .status(MemberStatus.MEMBER.wireValue())
                .createdAt(now)
                .answeredAt(now)
                .build());
        return id;
    }

    /** Паспорт; пусто — такого видео-чата нет. */
    public Optional<ConferenceRow> conference(String conferenceId) {
        return conferences.findById(conferenceId).map(row -> new ConferenceRow(
                row.getId(),
                ids.playerUids(List.of(row.getHost())).getOrDefault(row.getHost(), ""),
                CLOSED.equals(row.getStatus()),
                row.getGameRoomId(),
                row.getGameRoomCreatedAt() == null ? 0L : row.getGameRoomCreatedAt().toEpochMilli(),
                row.getCreatedAt().toEpochMilli(),
                row.getExpiresAt().toEpochMilli()));
    }

    /** Все строки участия видео-чата в порядке появления, любого состояния. */
    public List<MemberRow> members(String conferenceId) {
        return rows(members.findByConferenceIdOrderByCreatedAtAsc(conferenceId));
    }

    /** Участие названного игрока; пусто — его ни разу не звали. */
    public Optional<MemberRow> member(String conferenceId, String uid) {
        return members.findById(new VideoConferenceMemberId(conferenceId, ids.playerId(uid)))
                .map(row -> rows(List.of(row)).get(0));
    }

    /**
     * Позвать игрока: новая строка, либо возврат прежней в {@code invited}.
     *
     * <p>Отметка ответа при этом снимается: у нового приглашения ответа ещё
     * нет, каким бы ни был ответ на прошлое.
     */
    public void invite(String conferenceId, String uid, String inviterUid, Instant now) {
        ids.rememberPlayers(List.of(uid, inviterUid));
        VideoConferenceMemberId key = new VideoConferenceMemberId(conferenceId, ids.playerId(uid));
        VideoConferenceMember row = members.findById(key).orElseGet(() -> VideoConferenceMember.builder()
                .conferenceId(conferenceId)
                .player(key.getPlayer())
                .build());
        row.setStatus(MemberStatus.INVITED.wireValue());
        row.setInviter(ids.playerId(inviterUid));
        row.setGameRoomSeat(false);
        row.setCreatedAt(now);
        row.setAnsweredAt(null);
        members.save(row);
    }

    /**
     * Перевести участие в новое состояние.
     *
     * @return {@code false} — строки нет; состояние при этом менять нечему
     */
    public boolean setStatus(String conferenceId, String uid, MemberStatus status, Instant now) {
        VideoConferenceMember row = members
                .findById(new VideoConferenceMemberId(conferenceId, ids.playerId(uid)))
                .orElse(null);
        if (row == null) {
            return false;
        }
        row.setStatus(status.wireValue());
        row.setAnsweredAt(now);
        members.save(row);
        return true;
    }

    /**
     * Записать заведённую из видео-чата комнату и тех, кому в ней положено
     * место. Одним проходом по составу: отметка ставится названным и
     * снимается с остальных, если у комнаты новый состав.
     */
    public void attachGameRoom(String conferenceId, String roomId, Collection<String> seatedUids, Instant now) {
        VideoConference conference = conferences.findById(conferenceId).orElseThrow();
        conference.setGameRoomId(roomId);
        conference.setGameRoomCreatedAt(now);
        conferences.save(conference);
        LinkedHashSet<UUID> seated = new LinkedHashSet<>();
        for (String uid : seatedUids) {
            seated.add(ids.playerId(uid));
        }
        for (VideoConferenceMember row : members.findByConferenceIdOrderByCreatedAtAsc(conferenceId)) {
            boolean seat = seated.contains(row.getPlayer());
            if (row.isGameRoomSeat() != seat) {
                row.setGameRoomSeat(seat);
                members.save(row);
            }
        }
    }

    /** Свои приглашения без ответа, новые сверху. Срок проверяет вызывающий. */
    public List<InviteRow> pendingInvites(String uid) {
        List<VideoConferenceMember> pending = members
                .findByPlayerAndStatusOrderByCreatedAtDesc(ids.playerId(uid), MemberStatus.INVITED.wireValue());
        if (pending.isEmpty()) {
            return List.of();
        }
        List<String> conferenceIds = pending.stream().map(VideoConferenceMember::getConferenceId).toList();
        Map<String, VideoConference> passports = new java.util.HashMap<>();
        for (VideoConference conference : conferences.findAllById(conferenceIds)) {
            passports.put(conference.getId(), conference);
        }
        List<UUID> inviters = new ArrayList<>(pending.size());
        for (VideoConferenceMember row : pending) {
            if (row.getInviter() != null) {
                inviters.add(row.getInviter());
            }
        }
        Map<UUID, String> uids = ids.playerUids(inviters);
        List<InviteRow> result = new ArrayList<>(pending.size());
        for (VideoConferenceMember row : pending) {
            VideoConference passport = passports.get(row.getConferenceId());
            if (passport == null) {
                continue;
            }
            result.add(new InviteRow(
                    row.getConferenceId(),
                    row.getInviter() == null ? "" : uids.getOrDefault(row.getInviter(), ""),
                    row.getCreatedAt().toEpochMilli(),
                    CLOSED.equals(passport.getStatus()),
                    passport.getExpiresAt().toEpochMilli()));
        }
        return result;
    }

    /** Записать текст; вернуть номер сообщения. */
    public long appendText(String conferenceId, String senderUid, String text, Instant now) {
        ids.rememberPlayers(List.of(senderUid));
        return messages.saveAndFlush(VideoConferenceMessage.builder()
                .conferenceId(conferenceId)
                .sender(ids.playerId(senderUid))
                .body(text)
                .createdAt(now)
                .build()).getId();
    }

    /** Записать файл (с подписью или без); вернуть номер сообщения. */
    public long appendFile(String conferenceId, String senderUid, String text,
                           String fileKey, String fileName, String fileMime, long fileSize, Instant now) {
        ids.rememberPlayers(List.of(senderUid));
        return messages.saveAndFlush(VideoConferenceMessage.builder()
                .conferenceId(conferenceId)
                .sender(ids.playerId(senderUid))
                .body(text)
                .fileKey(fileKey)
                .fileName(fileName)
                .fileMime(fileMime)
                .fileSize(fileSize)
                .createdAt(now)
                .build()).getId();
    }

    /** Последние {@code limit} сообщений, старые сверху. */
    public List<MessageRow> messages(String conferenceId, int limit) {
        List<VideoConferenceMessage> window = new ArrayList<>(
                messages.findByConferenceIdOrderByIdDesc(conferenceId, Limit.of(limit)));
        java.util.Collections.reverse(window);
        List<UUID> senders = new ArrayList<>(window.size());
        for (VideoConferenceMessage message : window) {
            senders.add(message.getSender());
        }
        Map<UUID, String> uids = ids.playerUids(senders);
        List<MessageRow> result = new ArrayList<>(window.size());
        for (VideoConferenceMessage message : window) {
            result.add(new MessageRow(
                    message.getId(),
                    uids.getOrDefault(message.getSender(), ""),
                    message.getBody(),
                    message.getFileKey(),
                    message.getFileName(),
                    message.getFileMime(),
                    message.getFileSize() == null ? 0L : message.getFileSize(),
                    message.getCreatedAt().toEpochMilli()));
        }
        return result;
    }

    /**
     * Паспорт так, как его видит сценарий: хозяин назван uid, а не uuid.
     *
     * @param gameRoomId комната, заведённая этим составом; {@code null} — не заводили
     */
    public record ConferenceRow(String id, String hostUid, boolean closed, String gameRoomId,
                                long gameRoomCreatedAtMs, long createdAtMs, long expiresAtMs) {
    }

    /** Строка участия глазами сценария. */
    public record MemberRow(String uid, MemberStatus status, String inviterUid,
                            boolean gameRoomSeat, long createdAtMs, Long answeredAtMs) {
    }

    /** Своё ждущее приглашение вместе с тем, что о видео-чате нужно карточке. */
    public record InviteRow(String conferenceId, String inviterUid, long createdAtMs,
                            boolean conferenceClosed, long expiresAtMs) {
    }

    /** Сообщение чата; ключ файла едет как есть — ссылку подпишет хранилище. */
    public record MessageRow(long id, String senderUid, String text, String fileKey,
                             String fileName, String fileMime, long fileSize, long createdAtMs) {
    }

    /** Обратный перевод всех сторон списка — одним запросом на список. */
    private List<MemberRow> rows(List<VideoConferenceMember> found) {
        List<UUID> players = new ArrayList<>(found.size() * 2);
        for (VideoConferenceMember row : found) {
            players.add(row.getPlayer());
            if (row.getInviter() != null) {
                players.add(row.getInviter());
            }
        }
        Map<UUID, String> uids = ids.playerUids(players);
        List<MemberRow> result = new ArrayList<>(found.size());
        for (VideoConferenceMember row : found) {
            result.add(new MemberRow(
                    uids.getOrDefault(row.getPlayer(), ""),
                    MemberStatus.fromWire(row.getStatus()),
                    row.getInviter() == null ? null : uids.getOrDefault(row.getInviter(), ""),
                    row.isGameRoomSeat(),
                    row.getCreatedAt().toEpochMilli(),
                    row.getAnsweredAt() == null ? null : row.getAnsweredAt().toEpochMilli()));
        }
        return result;
    }
}
