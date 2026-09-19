package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.chat.api.dto.ChatRecordingAttachmentView;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.media.GameRecording;
import ru.hothat.repository.GetterMedia;
import ru.hothat.repository.SaverMedia;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Записи игр, которыми делятся в переписке.
 *
 * <p>Область записей ещё не переехала, поэтому карточка и право на просмотр
 * читаются из старых таблиц напрямую. Это переходный шов, и он собран в одном
 * классе намеренно: когда у {@code recording} появится свой командный порт
 * («открой запись собеседнику») и read-порт («опиши запись»), заменить нужно
 * будет два метода здесь, а не три сценария отправки.
 *
 * <p>Правила перенесены из {@code SocialServiceImpl.cleanAttachment} слово в
 * слово, вместе с кодами ошибок: поделиться можно только своей сохранённой
 * записью, и собеседник добавляется в список тех, кому она открыта, — иначе
 * карточка в переписке была бы кнопкой, ведущей в отказ.
 */
@Component
@RequiredArgsConstructor
public class SharedRecordingAccess {

    private final GetterMedia getterMedia;
    private final SaverMedia saverMedia;

    /**
     * Открыть запись собеседнику и описать её для карточки в переписке.
     *
     * <p>Запись и сообщение уходят одной транзакцией сценария: «карточка есть,
     * а доступа нет» — состояние, которое само не чинится.
     */
    public ChatRecordingAttachmentView share(HotHatUser user, String recordingId, String peerUid) {
        String id = recordingId == null ? "" : recordingId.trim();
        if (id.isEmpty()) {
            throw ApiException.of("RECORDING_REQUIRED");
        }
        GameRecording recording = getterMedia.getRecording(id)
                .orElseThrow(() -> ApiException.of("RECORDING_NOT_FOUND", 404));
        if (!recording.getSavedBy().contains(user.uid())) {
            throw ApiException.of("RECORDING_NOT_SAVED", 403);
        }
        List<String> sharedWith = new ArrayList<>(recording.getSharedWith());
        if (!sharedWith.contains(peerUid)) {
            sharedWith.add(peerUid);
            recording.setSharedWith(sharedWith);
            recording.setSharedCount(sharedWith.size());
            saverMedia.saveRecording(recording);
        }
        return view(recording);
    }

    /**
     * Карточки названных записей — одним запросом на всю страницу истории.
     *
     * <p>Записи, которой уже нет, в карте не будет: её карточку сценарий
     * рисует как пустую, а не выдумывает название.
     */
    public Map<String, ChatRecordingAttachmentView> describe(Collection<String> recordingIds) {
        Set<String> wanted = new LinkedHashSet<>();
        for (String id : recordingIds) {
            if (id != null && !id.isBlank()) {
                wanted.add(id);
            }
        }
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<String, ChatRecordingAttachmentView> byId = new HashMap<>();
        for (GameRecording recording : getterMedia.getRecordings(List.copyOf(wanted))) {
            byId.put(recording.getId(), view(recording));
        }
        return byId;
    }

    /** Название берётся у комнаты, как и раньше: у записи своего имени нет. */
    private static ChatRecordingAttachmentView view(GameRecording recording) {
        return new ChatRecordingAttachmentView(
                recording.getId(),
                Json.str(recording.getRoomName() == null ? recording.getRoomId() : recording.getRoomName(), 120),
                recording.getDurationNs() == null ? 0L : recording.getDurationNs(),
                recording.getGameLanguage());
    }
}
