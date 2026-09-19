package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.api.dto.SynthesizeSpeechRequestDTO;
import ru.hothat.testbot.api.dto.SynthesizedSpeechResponseDTO;
import ru.hothat.testbot.port.SpeechSynthesisPort;

/**
 * Озвучить реплику «облака мыслей» бота.
 *
 * <p>Заменяет {@code POST /api/tts} ({@code app-core.js:2106}).
 *
 * <p>Нажимает кнопку живой человек — владелец тестовой комнаты, — а голос
 * достаётся боту: реплика всплывает над его плиткой и звучит из неё. Поэтому
 * адрес и стоит среди адресов отряда: озвучивается речь бота, а кто нажал —
 * вопрос прав, а не принадлежности операции.
 *
 * <p>Комната приехала в адрес не для красоты. У старого {@code /api/tts}
 * комнаты нет вовсе, и вся его защита — роль {@code ADMIN}: любой админ мог
 * гнать через него произвольный текст откуда угодно, то есть держать наш
 * сервер бесплатным шлюзом к чужому синтезатору речи. Комната в пути
 * превращает клиентскую проверку {@code state.room?.isTestRoom}
 * ({@code app-core.js:1671}) в серверную: озвучка живёт ровно там, где ей
 * место, — в своей тестовой комнате вызывающего.
 *
 * <p>Транзакции нет: ни одна строка не меняется. Проверка комнаты — это одно
 * чтение, а дальше идёт сетевой вызов чужого сервиса, которому нечего делать
 * внутри транзакции.
 */
@Service
@RequiredArgsConstructor
public class SynthesizeSpeechUseCase {

    private final TestRoomFeatureGuard featureGuard;
    private final TestRoomOwnershipGuard ownershipGuard;
    private final SpeechSynthesisPort synthesizer;

    @PreAuthorize("hasRole('ADMIN')")
    public SynthesizedSpeechResponseDTO run(HotHatUser admin, String roomId,
                                            SynthesizeSpeechRequestDTO request) {
        featureGuard.requireEnabled();
        ownershipGuard.requireOwnedTestRoom(admin, roomId);
        SpeechSynthesisPort.Speech speech = synthesizer.synthesize(request.text(), request.voiceId());
        return new SynthesizedSpeechResponseDTO(
                speech.mime(), speech.audioBase64(), speech.voiceId(), speech.label(), speech.model());
    }
}
