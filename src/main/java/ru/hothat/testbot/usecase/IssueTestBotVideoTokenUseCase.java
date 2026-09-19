package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.usecase.RoomVideoTokens;
import ru.hothat.testbot.api.dto.IssueTestBotVideoTokenRequestDTO;
import ru.hothat.testbot.api.dto.TestBotVideoTokenResponseDTO;

/**
 * Выдать видеотокен тестовому боту.
 *
 * <p>Заменяет {@code POST /api/token} с чужим {@code participant_identity}
 * ({@code test-mode.js:286}) — единственный случай во всём продукте, когда
 * клиент просит токен от имени другого участника.
 *
 * <p>Отдельный адрес вместо необязательного поля в общем токене — не вкусовое
 * решение. В общем адресе делегирование выражено пустотой: без поля токен
 * выдаётся себе, с полем — другому, и одна схема описывает две операции с
 * разными правами. Разведённые адреса делают правило видимым: попросить за
 * бота можно только здесь, а сюда пускают только админа, и только в его
 * собственную тестовую комнату.
 *
 * <p>Выдачу оставляем {@link RoomVideoTokens}: там подпись JWT для LiveKit,
 * набор прав участника и HMAC-учётка TURN — повторить их здесь значило бы
 * завести вторую формулу допуска к видеосвязи. Движок токена и проверяет
 * делегирование по существу: личность обязана быть тестовым ботом
 * ({@code Ids.TEST_BOT}), комната — тестовой, вызывающий — её владельцем и
 * админом, а сам бот — уже сидящим за столом с пометкой {@code isTestBot}.
 * Регулярное выражение на поле — только первый заслон; настоящая привязка
 * бота к комнате проверяется по строке места.
 *
 * <p>Флаг {@code bot_enabled} требуется тот же, что у остальных адресов
 * отряда: выключенный тест-режим не должен оставлять живой одну щель, через
 * которую всё ещё раздают токены в видеокомнату.
 *
 * <p>Транзакция на чтение: сам токен ничего не пишет, но проверка комнаты,
 * места и дивизиона внутри движка читает несколько строк, и все они должны
 * приехать из одного снимка.
 */
@Service
@RequiredArgsConstructor
public class IssueTestBotVideoTokenUseCase {

    private final TestRoomFeatureGuard featureGuard;
    private final RoomVideoTokens videoTokens;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public TestBotVideoTokenResponseDTO run(HotHatUser admin, String roomId,
                                            IssueTestBotVideoTokenRequestDTO request) {
        featureGuard.requireEnabled();
        RoomVideoTokens.Issued issued = videoTokens.forPlayer(admin, roomId, request.botIdentity());
        return new TestBotVideoTokenResponseDTO(
                issued.serverUrl(), issued.participantToken(),
                issued.participantIdentity(), RoomVideoTokens.credentials(issued));
    }
}
