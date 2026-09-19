package ru.hothat.machine.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hothat.machine.domain.MachineRoute;
import ru.hothat.machine.domain.Secrets;
import ru.hothat.recording.spi.RecordingSignaturePort;

/**
 * Сверяет удостоверение машинного актора.
 *
 * <p>Один класс на все пять акторов — это и есть «одинаково для всех машинных
 * адресов». Сегодня то же самое размазано по четырём методам в трёх
 * контроллерах, и каждый решает по-своему: два сравнивают секрет обычным
 * {@code equals}, два берут подпись из строки запроса.
 *
 * <p>Секрет приезжает заголовком {@link MachineActorFilter#SECRET_HEADER}.
 * Строка запроса для этого не годится: она целиком попадает в журнал доступа
 * веб-сервера, в {@code Referer} при переходе со страницы и в историю
 * браузера, которым Egress открывает страницу рекордера. Тело не годится
 * тоже — тогда удостоверение нельзя проверить до разбора запроса.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MachineCredentialVerifier {

    private final RecordingSignaturePort signatures;

    @Value("${hot-hat.cron-secret:}")
    private String cronSecret;

    @Value("${monitor.secret:}")
    private String monitorSecret;

    /**
     * @param presented значение заголовка с секретом; {@code null}, если его не прислали
     * @return открыт ли этот адрес предъявленным удостоверением
     */
    public boolean verify(MachineRoute route, String presented) {
        return switch (route.actor()) {
            case RECORDER_BOOTSTRAP, RECORDER -> viewSignatureMatches(route, presented);
            case CRON -> Secrets.constantTimeEquals(cronSecret, presented);
            case MONITOR_AGENT -> Secrets.constantTimeEquals(monitorSecret, presented);
            // Вебхук удостоверяется подписью самого LiveKit и сюда не приходит.
            case EGRESS -> false;
        };
    }

    /**
     * Подпись съёмки считается от секрета LiveKit и от пары «комната + партия»,
     * поэтому подходит только к своему адресу.
     *
     * <p>Исключение перехвачено намеренно: незаданный {@code livekit.api-secret}
     * заставляет подпись бросить 503, а фильтр не
     * вправе ронять запрос ошибкой сервера — отсутствие ключа означает «не
     * удостоверен», и клиент должен увидеть 401, а не пятисотку.
     */
    private boolean viewSignatureMatches(MachineRoute route, String presented) {
        if (presented == null || presented.isBlank() || !route.scope().present()) {
            return false;
        }
        try {
            return signatures.verifyViewSignature(
                    route.scope().roomId(), route.scope().gameNumber(), presented);
        } catch (RuntimeException e) {
            log.warn("Подпись рекордера не проверена: {}", e.getMessage());
            return false;
        }
    }
}
