package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;

import java.time.Instant;
import java.util.Optional;

/**
 * Место наблюдателя, который смотрит превью с главной.
 *
 * <p>Раньше эту строку заводил и стирал сам браузер
 * ({@code live-preview.js:101-102} и {@code app-core.js:4852}). Отсюда и
 * повадки, которые здесь исправлены: время последнего появления писалось
 * часами клиента, а не сервера, а вкладка, закрытая мимо обработчика, навсегда
 * оставляла за собой зрителя.
 *
 * <p>Отдельный класс на три операции над одной строкой, потому что у всех трёх
 * общее важное правило: **настоящее зрительское место не трогаем**. Один и тот
 * же человек может смотреть комнату с игрового экрана и держать открытой
 * главную; превью не имеет права ни понизить его место до лёгкой подписки, ни
 * стереть его, закрывая свою вкладку.
 */
@Component
@RequiredArgsConstructor
public class RoomPreviewSeats {

    /** Так подписан наблюдатель, у которого нет имени в профиле. */
    private static final String FALLBACK_NAME = "Зритель";

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;

    /**
     * Заводит или продлевает место превью; возвращает отметку сервера.
     *
     * <p>У найденного настоящего зрителя обновляется только время: признак
     * {@code preview} остаётся как был, иначе зритель партии перестал бы
     * попадать в состав комнаты, просто открыв главную в соседней вкладке.
     */
    public long open(String roomId, String uid, String viewerName) {
        long now = System.currentTimeMillis();
        RoomSpectator seat = getterRoom.getSpectator(roomId, uid).orElse(null);
        if (seat == null) {
            seat = RoomSpectator.builder()
                    .roomId(roomId)
                    .uid(uid)
                    .joinedAt(Instant.now())
                    .role("spectator")
                    .preview(true)
                    .build();
            seat.setName(name(viewerName));
        } else if (Boolean.TRUE.equals(seat.getPreview()) || isBlank(seat.getName())) {
            // Имя настоящего зрителя не переписываем: под ним он уже сидит в
            // комнате, и подменять его тем, что лежит в токене, незачем.
            seat.setName(name(viewerName));
        }
        seat.setLastSeenAt(now);
        saverRoom.saveSpectator(seat);
        return now;
    }

    /**
     * Отмечает, что превью ещё открыто.
     *
     * <p>Места нет — значит сессию уже закрыли или подмели: отвечаем 404, и
     * клиент откроет новую. Молча заводить место здесь нельзя: тогда
     * подтверждение стало бы созданием, и закрытая сессия воскресала бы сама.
     */
    public long touch(String roomId, String uid) {
        RoomSpectator seat = getterRoom.getSpectator(roomId, uid)
                .orElseThrow(() -> ApiException.of("PREVIEW_SESSION_NOT_FOUND", 404));
        long now = System.currentTimeMillis();
        seat.setLastSeenAt(now);
        saverRoom.saveSpectator(seat);
        return now;
    }

    /**
     * Убирает место превью.
     *
     * <p>Настоящее зрительское место не трогает: закрытая вкладка главной не
     * должна выкидывать человека из комнаты, которую он смотрит с игрового
     * экрана. Отсутствие места — не ошибка: закрывать нечего.
     */
    public void close(String roomId, String uid) {
        Optional<RoomSpectator> seat = getterRoom.getSpectator(roomId, uid);
        if (seat.isPresent() && Boolean.TRUE.equals(seat.get().getPreview())) {
            saverRoom.deleteSpectator(roomId, uid);
        }
    }

    private static String name(String viewerName) {
        String cleaned = viewerName == null ? "" : viewerName.trim();
        return cleaned.isEmpty() ? FALLBACK_NAME : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
