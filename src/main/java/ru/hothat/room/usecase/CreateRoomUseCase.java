package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.CreateRoomRequestDTO;
import ru.hothat.room.api.dto.CreatedRoomResponseDTO;

/**
 * Завести комнату и сесть в неё хозяином.
 *
 * <p>Комната и первое место пишутся одной транзакцией — это строка 2 таблицы
 * девяти транзакций через границу (§7.3 плана). До сих пор их писал пакет в
 * браузере ({@code createRoom()}, {@code app-core.js:12101}), и между двумя
 * записями было окно, в котором комната существовала пустой: уборщик
 * брошенных комнат вправе снести именно такую.
 *
 * <p>Идентификатор, хозяина, фазу, дивизион и счётчик присутствия ставит
 * сервер. Раньше их назначал себе клиент — включая {@code createdBy}, то есть
 * хозяйство было полем, которое вызывающий вписывал сам (находка A1).
 *
 * <p>Сама сборка комнаты живёт в {@link RoomFounding}: ею же пользуется
 * видео-чат, заводя комнату «этим составом», и правила у обоих одни.
 */
@Service
@RequiredArgsConstructor
public class CreateRoomUseCase {

    private final RoomFounding founding;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public CreatedRoomResponseDTO run(HotHatUser user, CreateRoomRequestDTO request) {
        // Тестовая комната — админский инструмент: в ней сидят боты и включены
        // владельческие поблажки. Постороннему флаг не отказывают, а
        // игнорируют: он приезжает из адресной строки, и падать из-за
        // случайного «?test=1» комната не должна.
        boolean testRoom = request.testRoomOrDefault() && user.admin();
        RoomFounding.Founded founded = founding.found(new RoomFounding.Order(
                user.uid(),
                request.name(),
                request.capacityOrDefault(),
                request.privateRoomOrDefault(),
                request.gameModeOrDefault().wireValue(),
                request.gameLanguage() == null ? null : request.gameLanguage().wireValue(),
                testRoom));
        return new CreatedRoomResponseDTO(projections.room(founded.room()),
                projections.seat(founded.seat().player(), founded.nowMs()));
    }
}
