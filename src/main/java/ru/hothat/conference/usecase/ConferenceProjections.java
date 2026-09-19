package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.conference.api.dto.ConferenceFileView;
import ru.hothat.conference.api.dto.ConferenceGameRoomView;
import ru.hothat.conference.api.dto.ConferenceMessageView;
import ru.hothat.conference.api.dto.ConferencePlayerView;
import ru.hothat.conference.api.dto.ConferenceView;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.store.ConferenceFileStorage;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.room.spi.RoomDirectoryPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Видео-чат и его лента в форме ответа.
 *
 * <p>Одно место сборки на HTTP-ответы и кадры канала: экран созвона кормится
 * одной формой, откуда бы она ни приехала. Ники и аватары — из карточек
 * игроков, по одному запросу на список, а не по одному на человека.
 */
@Component
@RequiredArgsConstructor
public class ConferenceProjections {

    private final PlayerCardPort cards;
    private final ConferenceFileStorage files;
    /** Комната из созвона показывается, пока набирается; сыгранная или закрытая — уже нет. */
    private final RoomDirectoryPort rooms;

    public ConferenceView view(ConferenceAccess.Opened opened) {
        List<ConferenceStore.MemberRow> participants = opened.participants();
        List<ConferenceStore.MemberRow> invited = opened.invited();
        Set<String> uids = new LinkedHashSet<>();
        participants.forEach(row -> uids.add(row.uid()));
        invited.forEach(row -> uids.add(row.uid()));
        Map<String, PlayerCardPort.Card> known = cards.cards(uids);

        ConferenceStore.ConferenceRow conference = opened.conference();
        // Отметка о комнате переживает саму комнату: после партии ссылка на
        // созвон не должна снова уводить всех за закрытый стол. Поэтому в
        // кадре комната есть, только пока она набирается.
        ConferenceGameRoomView gameRoom = null;
        if (conference.gameRoomId() != null && !conference.gameRoomId().isBlank()
                && rooms.find(conference.gameRoomId()).map(RoomDirectoryPort.RoomBrief::open).orElse(false)) {
            List<String> seated = new ArrayList<>();
            for (ConferenceStore.MemberRow row : opened.members()) {
                if (row.gameRoomSeat()) {
                    seated.add(row.uid());
                }
            }
            gameRoom = new ConferenceGameRoomView(conference.gameRoomId(), seated, conference.gameRoomCreatedAtMs());
        }
        return new ConferenceView(
                conference.id(),
                conference.hostUid(),
                players(participants, known),
                players(invited, known),
                ConferenceRules.MAX_PARTICIPANTS,
                conference.expiresAtMs(),
                gameRoom);
    }

    public List<ConferenceMessageView> messages(List<ConferenceStore.MessageRow> rows) {
        Set<String> uids = new LinkedHashSet<>();
        rows.forEach(row -> uids.add(row.senderUid()));
        Map<String, PlayerCardPort.Card> known = cards.cards(uids);
        List<ConferenceMessageView> result = new ArrayList<>(rows.size());
        for (ConferenceStore.MessageRow row : rows) {
            result.add(message(row, known.get(row.senderUid())));
        }
        return result;
    }

    public ConferenceMessageView message(ConferenceStore.MessageRow row) {
        return message(row, cards.card(row.senderUid()).orElse(null));
    }

    private ConferenceMessageView message(ConferenceStore.MessageRow row, PlayerCardPort.Card sender) {
        ConferenceFileView file = null;
        if (row.fileKey() != null && !row.fileKey().isBlank()) {
            file = new ConferenceFileView(row.fileName(), row.fileMime(), row.fileSize(),
                    files.presignView(row.fileKey()));
        }
        return new ConferenceMessageView(
                row.id(),
                row.senderUid(),
                sender == null ? cards.nicknameOf(row.senderUid()) : sender.nickname(),
                sender == null ? null : blankToNull(sender.avatarDataUrl()),
                row.text(),
                file,
                row.createdAtMs());
    }

    private List<ConferencePlayerView> players(List<ConferenceStore.MemberRow> rows,
                                               Map<String, PlayerCardPort.Card> known) {
        List<ConferencePlayerView> result = new ArrayList<>(rows.size());
        for (ConferenceStore.MemberRow row : rows) {
            PlayerCardPort.Card card = known.get(row.uid());
            result.add(new ConferencePlayerView(
                    row.uid(),
                    card == null ? cards.nicknameOf(row.uid()) : card.nickname(),
                    card == null ? null : blankToNull(card.avatarDataUrl())));
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
