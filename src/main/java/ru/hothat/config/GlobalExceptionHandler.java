package ru.hothat.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import ru.hothat.dto.common.ErrorDTO;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Тело ответа — {@link ErrorDTO}: прежние {@code error} и {@code code} плюс
 * статус, адрес, момент и {@code requestId}, по которому отказ находится в
 * логе сервера. Собирает его {@link ErrorResponses}, здесь только выбор кода.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final boolean exposeDetails;

    /**
     * @param exposeDetails показывать ли в теле 500 причину исключения. Пока
     *                      игра не открыта людям, это экономит поход в лог;
     *                      потом — выключить: в причине может быть SQL.
     */
    public GlobalExceptionHandler(@Value("${hot-hat.errors.expose-details:false}") boolean exposeDetails) {
        this.exposeDetails = exposeDetails;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDTO> handleApi(ApiException e, HttpServletRequest request) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponses.of(request, e.getStatus(), e.getCode()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorDTO> handleAuth(AuthenticationException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponses.of(request, 401, "AUTH_REQUIRED"));
    }

    /**
     * Сюда приходит отказ {@code @PreAuthorize} на сценарии — то есть почти
     * каждый отказ по правам в {@code /api/v2}.
     *
     * <p>Код зависит от того, кому отказали. Гостю {@code ADMIN_REQUIRED} —
     * тупик: он и не претендовал на консоль, а из «нет прав администратора» не
     * следует, что делать. Ему нужна регистрация, и так и сказано. Игроку без
     * администраторской роли остаётся прежний код: фронтенд разбирает его на
     * админском экране.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDTO> handleDenied(AccessDeniedException e, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HotHatUser user = authentication != null
                && authentication.getPrincipal() instanceof HotHatUser principal ? principal : null;
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponses.of(request, 403, deniedCode(user)));
    }

    /**
     * Кому отказали — тот и назван. Гостю {@code REGISTRATION_REQUIRED}:
     * «нет прав администратора» для него тупик — он и не претендовал на
     * консоль, а из этого текста не следует, что делать, тогда как «нужна
     * регистрация» ведёт прямо на форму. Игроку остаётся прежний код: его
     * разбирает админский экран.
     *
     * <p>Публичный, потому что тем же правилом пользуется отказ именованного
     * канала {@code /ws/v2}: {@code @PreAuthorize} на сценарии канала бросает
     * тот же {@code AccessDeniedException}, и код отказа обязан совпасть с
     * тем, что вернул бы HTTP, — иначе у клиента появится вторая ветка
     * разбора отказов, ровно та, от которой каналы и уводят.
     */
    public static String deniedCode(HotHatUser user) {
        return user != null && user.guest() ? "REGISTRATION_REQUIRED" : "ADMIN_REQUIRED";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDTO> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        return validationFailed(request, e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage()));
    }

    /**
     * Одна форма ответа на все четыре способа не пройти проверку: клиенту
     * незачем знать, тело это было, параметр строки запроса или кусок пути.
     */
    private ResponseEntity<ErrorDTO> validationFailed(HttpServletRequest request, Stream<String> messages) {
        List<String> details = messages
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponses.validation(request, details));
    }

    /**
     * Не прошла проверка тела, разобранного как объект-параметр:
     * {@code @Valid @ParameterObject} у страничных запросов v2 отдаёт именно
     * {@code BindException}, а не {@link MethodArgumentNotValidException}.
     * Обработчик выше остаётся более точным и продолжает ловить тела запросов
     * сам — {@code MethodArgumentNotValidException} наследует {@code BindException}.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorDTO> handleBind(BindException e, HttpServletRequest request) {
        return validationFailed(request, e.getFieldErrors().stream().map(err -> err.getDefaultMessage()));
    }

    /**
     * Не прошла проверка отдельного аргумента метода: {@code @RoomId} на пути,
     * {@code @Min} у параметра строки запроса. Такие нарушения объявились
     * вместе с областями {@code /api/v2}, и без этих двух обработчиков опечатка
     * в адресе доезжала до {@code handleOther} — клиент получал 500, а в лог
     * ложился стектрейс, будто упал сервер.
     *
     * <p>Форм две, потому что путей проверки два: при {@code @Validated} на
     * классе аргументы проверяет AOP-обёртка и бросает
     * {@code ConstraintViolationException}, а встроенная проверка Spring MVC —
     * {@code HandlerMethodValidationException}. Какой из путей выберет
     * контейнер, зависит от того, обёрнут ли контроллер прокси, поэтому
     * объявлены оба.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDTO> handleConstraint(ConstraintViolationException e, HttpServletRequest request) {
        return validationFailed(request, e.getConstraintViolations().stream().map(v -> v.getMessage()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorDTO> handleMethodValidation(HandlerMethodValidationException e,
                                                           HttpServletRequest request) {
        return validationFailed(request, e.getAllErrors().stream().map(err -> err.getDefaultMessage()));
    }

    /**
     * Тело запроса не разобралось: сломанный JSON, строка вместо числа,
     * незнакомое значение перечисления. Это ошибка клиента, а не сервера, но
     * без этого обработчика {@code HttpMessageNotReadableException} доезжал до
     * {@link #handleOther} — опечатка в теле давала 500 и стектрейс в логе.
     *
     * <p>Причину наружу не выносим: в сообщении Jackson лежат имена классов и
     * путь по полям DTO. Клиенту хватает того, что тело некорректно.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDTO> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("Некорректное тело запроса: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(request, 400, "VALIDATION_FAILED", "Некорректное тело запроса."));
    }

    /**
     * В адресе стоит не то, что объявлено: буквы там, где ждали число
     * ({@code /friends/requests/undefined/acceptance}), или незнакомое
     * значение перечисления. Spring разбирает путь до вызова контроллера, и
     * проверки {@code @Positive} до этого места не доходят — поэтому без
     * обработчика {@code MethodArgumentTypeMismatchException} падал в
     * {@link #handleOther} и опечатка в адресе давала 500 «Сервер временно
     * недоступен». Это ошибка клиента: 15 адресов v2 несут числовой параметр
     * пути, и по каждому из них 500 маскировал бы отказ сервера.
     *
     * <p>Наружу отдаём только имя параметра: в самом исключении лежат имена
     * java-классов, а по ним клиенту нечего делать.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDTO> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e,
            HttpServletRequest request) {
        log.warn("Некорректное значение параметра {}: {}", e.getName(), e.getValue());
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(request, 400, "VALIDATION_FAILED",
                        "Некорректное значение параметра «" + e.getName() + "»."));
    }

    /**
     * Обращение к несуществующему маршруту. Без этого Spring отдавал 500 и
     * писал в лог стектрейс, будто упал сервер, — искать опечатку в адресе
     * по такому логу невозможно.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorDTO> handleNotFound(
            org.springframework.web.servlet.resource.NoResourceFoundException e, HttpServletRequest request) {
        log.warn("Неизвестный маршрут: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponses.of(request, 404, "NOT_FOUND", "Маршрут не найден."));
    }

    /**
     * Адрес есть, а метод не тот: POST на читающий адрес, DELETE на создающий.
     * Это ошибка клиента, и у неё свой код — 405 с заголовком Allow, по
     * которому видно, чем адрес отвечает на самом деле. Без этого обработчика
     * {@code HttpRequestMethodNotSupportedException} доезжал до
     * {@link #handleOther}: опечатка в методе давала 500 и стектрейс в логе,
     * то есть выглядела как авария сервера, а не как промах вызывающего.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDTO> handleMethodNotAllowed(
            org.springframework.web.HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("Метод {} не поддержан адресом", e.getMethod());
        ErrorDTO body = ErrorResponses.of(request, 405, "METHOD_NOT_ALLOWED",
                "Метод не поддерживается этим адресом.");
        Set<HttpMethod> allowed = e.getSupportedHttpMethods();
        // Allow обязателен для 405 по HTTP: без него клиенту негде узнать,
        // какие методы адрес принимает.
        if (allowed == null || allowed.isEmpty()) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(allowed.toArray(new HttpMethod[0]))
                .body(body);
    }

    /**
     * Тело пришло в чужом типе: форма вместо JSON, текст без заголовка.
     * Тот же разряд промаха вызывающего, что и метод не тот, и та же беда без
     * обработчика — 500 вместо 415.
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorDTO> handleUnsupportedMediaType(
            org.springframework.web.HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        log.warn("Неподдержанный тип тела: {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponses.of(request, 415, "UNSUPPORTED_MEDIA_TYPE",
                        "Тело запроса нужно присылать как application/json."));
    }

    /**
     * Клиент оборвал запрос: ушёл со страницы, закрыл вкладку, отменил
     * загрузку. Ответ писать уже некому, и это не ошибка сервера — но
     * стектрейс на полтораста строк выглядел как авария и прятал настоящие.
     */
    @ExceptionHandler(org.apache.catalina.connector.ClientAbortException.class)
    public ResponseEntity<ErrorDTO> handleClientAbort(
            org.apache.catalina.connector.ClientAbortException e) {
        log.debug("Клиент оборвал соединение: {}", e.getMessage());
        return null;
    }

    /**
     * Гонка, которую последним словом разрешила база.
     *
     * <p>Проверки «ник свободен» и «почта свободна» стоят в сценариях ради
     * понятной ошибки, но между проверкой и вставкой помещается чужой коммит:
     * два одновременных переименования в одно имя доходят до базы оба, и
     * второе останавливает уникальный индекс. До этого обработчика такой
     * отказ выглядел как авария сервера — 500 и стектрейс, — хотя случилось
     * ровно то, что должно было: имя занято.
     *
     * <p>Имя ограничения переводится в код ошибки, потому что человеку у
     * формы важно, что именно занято. Незнакомое ограничение отвечает общим
     * {@code CONFLICT}: молчать о нём нельзя, а угадывать — тем более.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDTO> handleConflict(
            org.springframework.dao.DataIntegrityViolationException e, HttpServletRequest request) {
        String cause = String.valueOf(e.getMostSpecificCause().getMessage());
        String code = "CONFLICT";
        if (cause.contains("ux_player_profile_nickname")) {
            code = "NICKNAME_TAKEN";
        } else if (cause.contains("ux_user_account_email")) {
            code = "EMAIL_TAKEN";
        } else if (cause.contains("ux_user_ban_active")) {
            code = "ALREADY_BANNED";
        } else {
            log.warn("Нарушено ограничение базы, кода для него нет: {}", cause);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.of(request, 409, code));
    }

    /**
     * Строку изменили, пока её правил кто-то другой: {@code @Version} не сошёлся.
     *
     * <p>Ответ 409, а не 500: данные целы, повторить запрос можно, и клиент
     * умеет показать это человеку. Так ведут себя карточка игрока, команда и
     * сессия предматчевой проверки — все, у кого §6.4 плана требует версии.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDTO> handleConcurrentUpdate(
            org.springframework.orm.ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.of(request, 409, "CONCURRENT_UPDATE"));
    }

    /**
     * Всё, для чего обработчика выше не нашлось, — авария сервера.
     *
     * <p>В лог — стектрейс целиком; идентификатор запроса в строке лога тот
     * же, что клиент получает в теле, так что от красного блока в консоли
     * браузера до причины один поиск. Сама причина в тело попадает только при
     * включённом {@code hot-hat.errors.expose-details}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> handleOther(Exception e, HttpServletRequest request) {
        log.error("Необработанное исключение на {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponses.internal(request, exposeDetails ? describe(e) : null));
    }

    /**
     * Исключение и его корень в одну строку: у Spring обёртка почти всегда
     * общая ({@code TransactionSystemException}, {@code DataAccessException}),
     * а сказать, что случилось, может только самая глубокая причина.
     */
    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String head = e.getClass().getSimpleName() + ": " + e.getMessage();
        return root == e ? head : head + " ← " + root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
