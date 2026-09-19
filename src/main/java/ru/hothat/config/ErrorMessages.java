package ru.hothat.config;

import java.util.Map;

/**
 * Тексты ошибок дословно перенесены из sendError() lib/server.js и словаря
 * api/game.js: фронтенд показывает их пользователю как есть.
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static final Map<String, String> BY_CODE = Map.ofEntries(
            Map.entry("AUTH_REQUIRED", "Требуется вход в аккаунт."),
            Map.entry("AUTH_INVALID", "Сессия истекла. Войдите снова."),
            Map.entry("REGISTRATION_REQUIRED", "Для этой функции нужна регистрация."),
            Map.entry("USER_BANNED", "Аккаунт заблокирован администратором."),
            Map.entry("ADMIN_REQUIRED", "Нет прав администратора."),
            // Отказ по чужой заявке в друзья. Ответ один и на чужую, и на
            // несуществующую: по номеру заявки нельзя узнать, есть ли она.
            Map.entry("REQUEST_FORBIDDEN", "Заявка недоступна."),
            Map.entry("FEATURE_DISABLED", "Возможность выключена."),
            // Отказы именованных каналов /ws/v2. Их читает не разработчик, а
            // игрок: кадр ошибки канала имеет ту же форму, что ответ HTTP, и
            // фронтенд показывает его поле error как есть. Без строки здесь на
            // экране появилось бы «UNKNOWN_FRAME».
            Map.entry("BAD_MESSAGE", "Некорректный запрос."),
            Map.entry("UNKNOWN_FRAME", "Канал не понимает этот запрос."),
            Map.entry("CHANNEL_UNAVAILABLE", "Живые обновления временно недоступны."),
            // Своя авторизация: тексты видит пользователь на формах входа
            // и регистрации, поэтому они не должны совпадать с кодом.
            Map.entry("INVALID_CREDENTIALS", "Неверная почта или пароль."),
            Map.entry("INVALID_EMAIL", "Некорректный e-mail."),
            Map.entry("WEAK_PASSWORD", "Слишком короткий пароль: минимум 6 символов."),
            Map.entry("EMAIL_TAKEN", "Этот e-mail уже занят."),
            Map.entry("INVALID_NICKNAME", "Ник: латиница, цифры и подчёркивание, 3–20 символов, начинается с буквы."),
            Map.entry("NICKNAME_TAKEN", "Этот ник уже занят."),
            // Коды кластера личности. Первые три пользователь видит на формах
            // профиля, четвёртый — при попытке сменить дивизион; без текста на
            // экране появлялась бы сама строка кода.
            Map.entry("NICKNAME_INVALID",
                    "Ник: латиница, цифры и подчёркивание, 3–20 символов, начинается с буквы."),
            Map.entry("AVATAR_REQUIRED", "Не выбрана аватарка."),
            Map.entry("AVATAR_INVALID",
                    "Аватарка должна быть картинкой webp, jpeg или png и весить меньше 90 КБ."),
            Map.entry("ROOM_REQUIRED", "Не указана комната."),
            // Гонки, которые разрешает база. Без текста человек у формы видел
            // бы машинную строку кода вместо объяснения.
            Map.entry("CONFLICT", "Данные изменились: повторите попытку."),
            Map.entry("CONCURRENT_UPDATE", "Кто-то менял это одновременно с вами. Повторите попытку."),
            Map.entry("ALREADY_BANNED", "Игрок уже заблокирован."),
            Map.entry("ALREADY_REGISTERED", "Аккаунт уже зарегистрирован."),
            Map.entry("INVALID_REFRESH_TOKEN", "Сессия истекла. Войдите снова."),
            // Смена своего пароля в /api/v2/auth: текущий пароль указан неверно.
            // Отдельный код и 403, а не общий INVALID_CREDENTIALS с 401: сессия жива,
            // и текст «Войдите снова» здесь был бы прямым враньём (замечание D3 аудита).
            Map.entry("PASSWORD_MISMATCH", "Текущий пароль указан неверно."),
            Map.entry("INVALID_RESET_TOKEN", "Ссылка восстановления недействительна или устарела."),
            Map.entry("USER_NOT_FOUND", "Аккаунт не найден."),
            Map.entry("INVALID_DISPLAY_NAME", "Имя не может быть пустым."),
            Map.entry("QUERY_NOT_SCOPED", "Запрос должен ограничиваться своими данными."),
            Map.entry("EMAIL_IMMUTABLE", "Почту нельзя изменить этим способом."),
            Map.entry("DIVISION_IMMUTABLE", "Дивизион выбирается один раз и не меняется."),
            Map.entry("INVALID_DIVISION", "Неизвестный дивизион."),
            Map.entry("READ_FORBIDDEN", "Нет доступа к этим данным."),
            Map.entry("WRITE_FORBIDDEN", "Эти данные меняет только сервер."),
            // Без текста пользователь видел бы на экране саму строку кода —
            // как это и происходило со 110 кодами из 174 прежнего слоя.
            Map.entry("ROOM_MEMBER_ONLY", "Это действие доступно только участникам комнаты."),
            Map.entry("FIELD_FORBIDDEN", "Это поле меняет только сервер."),
            // Коды областей /api/v2. Без текста пользователь видит на экране саму
            // строку кода — так сегодня ведут себя 110 кодов из 174, и повторять
            // это в новых областях незачем.
            Map.entry("TEAM_NOT_FOUND", "Команда не найдена."),
            Map.entry("INVITE_NOT_FOUND", "Приглашение не найдено или уже отвечено."),
            Map.entry("PARTNER_MUST_BE_FRIEND", "Напарником можно позвать только друга."),
            Map.entry("RANKED_TEAM_REQUIRED", "Нужна подтверждённая рейтинговая команда."),
            // Пара распалась на половину: в подтверждённой команде остался один
            // человек. Чинить её нечем, и в рейтинговую партию такую не пускают.
            Map.entry("RANKED_TEAM_INVALID", "Команда собрана неправильно: в паре должно быть двое."),
            Map.entry("PREFLIGHT_REQUIRED", "Сначала пройдите проверку связи."),
            // Очередь и вход в рейтинговую комнату проверяют не свою готовность,
            // а готовность пары целиком: напарник мог ещё не нажать «готов».
            Map.entry("TEAM_PREFLIGHT_NOT_READY", "Пара ещё не готова: дождитесь напарника."),
            Map.entry("PARTNER_SELF", "Напарником нельзя позвать самого себя."),
            Map.entry("MATCH_TICKET_NOT_FOUND", "Заявка на подбор не найдена."),
            Map.entry("PREVIEW_SESSION_NOT_FOUND", "Просмотр комнаты не найден."),
            Map.entry("MEME_NOT_FOUND", "Мем не найден."),
            Map.entry("MEME_MEDIA_MISSING", "У мема нет файла."),
            Map.entry("NOT_MEME_AUTHOR", "Удалить мем может только тот, кто его загрузил."),
            Map.entry("BUILTIN_MEME_PROTECTED", "Встроенный мем удалить нельзя."),
            Map.entry("MEDIA_PATH_INVALID", "Некорректный путь к файлу."),
            // Расширение файла в ключе объекта не из нашего списка. Текст
            // отдельный на ролик и на обложку: игрок выбирает их разными
            // кнопками, и «не тот формат» без указания какого именно
            // заставляет гадать, что перезагружать.
            Map.entry("MEME_MIME_INVALID", "Такой формат ролика не поддерживается."),
            Map.entry("MEME_POSTER_MIME_INVALID", "Такой формат обложки не поддерживается."),
            Map.entry("RECORDING_NOT_FOUND", "Запись не найдена."),
            Map.entry("RECORDING_NOT_SAVED", "Запись ещё не сохранена."),
            // Карточку записи в переписку шлют кнопкой, но идентификатор приезжает
            // из тела: пустой — это не «не найдено», а «не сказано, какую».
            Map.entry("RECORDING_REQUIRED", "Не выбрана запись."),
            // Заливка сжатого ролика из админки: тело пришло без самой картинки.
            Map.entry("VIDEO_DATA_REQUIRED", "Не передан ролик."),
            // Коды машинной поверхности /api/v2/machine. Их видит не игрок, а
            // рекордер, планировщик и агент мониторинга, но правило одно на всех:
            // без текста в ответе поедет сама строка кода — так сегодня ведут
            // себя 110 кодов из 174.
            Map.entry("RECORDING_DISABLED", "Запись партии в этой комнате выключена."),
            Map.entry("RECORDING_GAME_NUMBER_MISMATCH", "Комната уже перешла к другой партии."),
            Map.entry("RECORDING_CEREMONY_NOT_READY", "Партия ещё не закончена: завершать запись рано."),
            Map.entry("DOCUMENT_CONFLICT", "Данные изменились. Повторите действие."),
            // Коды админской консоли. Их видит администратор в тосте админки,
            // и машинная строка на этом месте бесполезна ровно так же.
            Map.entry("BAN_TARGET_NOT_FOUND", "Аккаунт для блокировки не найден."),
            Map.entry("BAN_SELF_FORBIDDEN", "Себя заблокировать нельзя."),
            Map.entry("RECORDING_NOT_READY", "Файл записи ещё не готов."),
            Map.entry("S3_MEDIA_STORAGE_NOT_CONFIGURED", "Файловое хранилище не настроено."),
            // Ниже — коды, которые бросают ещё старые сервисы, но наружу их
            // теперь показывают адреса /api/v2: каждый из них назван в
            // @ApiResponse нового контроллера. Без строки здесь resolve()
            // возвращает сам код, и игрок читает на экране «ROOM_FULL».
            // Дружба.
            Map.entry("FRIEND_SELF", "Нельзя добавить в друзья самого себя."),
            Map.entry("ALREADY_FRIENDS", "Вы уже друзья."),
            Map.entry("REQUEST_EXISTS", "Заявка этому игроку уже отправлена."),
            Map.entry("REQUEST_NOT_FOUND", "Заявка не найдена или на неё уже ответили."),
            // Один текст на оба случая: и «переписка только с друзьями» (409),
            // и «этот игрок вам не друг» (403) — для игрока это одно и то же.
            Map.entry("FRIEND_REQUIRED", "Это доступно только друзьям."),
            // Проверка тела ловит пустую строку, а этот код — сообщение из одних
            // пробелов: после нормализации от него ничего не остаётся.
            Map.entry("MESSAGE_EMPTY", "Сообщение не может быть пустым."),
            Map.entry("TEAMMATE_MUST_REMAIN_FRIEND", "Сначала распустите команду: напарник должен остаться другом."),
            // Игрок и профиль.
            // Код служит и «такого игрока нет» (404), и «вы не участник» (403).
            // Общий текст выбран по первому: 403 приходит на служебных проверках,
            // где фронтенд показывает свой экран, а не строку ошибки.
            Map.entry("PLAYER_NOT_FOUND", "Игрок не найден."),
            Map.entry("DIVISION_LOCKED", "Дивизион уже выбран и не меняется."),
            Map.entry("SABOTAGE_LIMIT_REACHED", "Бесплатные партии с диверсиями закончились."),
            // Команда.
            Map.entry("ALREADY_IN_TEAM", "Вы уже состоите в команде."),
            Map.entry("PARTNER_NOT_FOUND", "Игрок с таким ником не найден."),
            Map.entry("PARTNER_ALREADY_IN_TEAM", "Этот игрок уже состоит в другой команде."),
            Map.entry("TEAM_NAME_TAKEN", "Такое название команды уже занято."),
            Map.entry("TEAM_DIVISION_MISMATCH", "Команда играет в другом дивизионе."),
            Map.entry("MEDIA_NOT_READY", "Сначала проверьте камеру и микрофон."),
            Map.entry("TEAMMATE_SABOTAGE_LIMIT_REACHED",
                    "У напарника закончились бесплатные партии с диверсиями."),
            // Подбор и рейтинговая комната.
            Map.entry("QUICK_LANGUAGE_INVALID",
                    "Быстрая игра идёт на языке своего дивизиона или на английском."),
            Map.entry("PREFLIGHT_CAPTAIN_ONLY", "Это действие доступно только тому, кто начал проверку связи."),
            Map.entry("PREFLIGHT_ROOM_MISMATCH", "Проверка связи была начата для другой комнаты."),
            Map.entry("RANKED_ROOM_UNAVAILABLE", "Рейтинговая комната больше недоступна."),
            Map.entry("RANKED_ROOM_MODE_MISMATCH", "В этой комнате другой режим партии."),
            Map.entry("RANKED_ROOM_DIVISION_MISMATCH", "Эта комната играет в другом дивизионе."),
            Map.entry("ROOM_FULL", "В комнате нет свободных мест."),
            // Итоги рейтинговой партии.
            Map.entry("NOT_RANKED_RESULT", "Это не итог рейтинговой партии."),
            Map.entry("RANKED_TEAMS_MISSING", "В партии не хватает рейтинговых команд."),
            // Записи партий.
            Map.entry("RECORDING_PARTICIPANT_ONLY", "Запись доступна только участникам партии."),
            Map.entry("RECORDING_NOT_AVAILABLE", "Запись недоступна."),
            // Складывать запись некуда: отдельный код от медийного хранилища,
            // потому что бакеты разные и настраивают их порознь. Отвечает 503,
            // и говорить надо про сервер, а не про действие игрока.
            Map.entry("S3_RECORDING_STORAGE_NOT_CONFIGURED", "Хранилище записей не настроено."),
            // Тестовая комната: код есть в ErrorCode, а текста к нему не было.
            Map.entry("TEST_ROOM_ONLY", "Действие доступно только в своей тестовой комнате."),
            // Делегированный видеотокен: токен за другого участника берут
            // только в своей тестовой комнате и только за бота, который уже
            // сидит за столом. Три отказа названы порознь — админ по тексту
            // должен понять, ошибся он комнатой, именем или моментом.
            Map.entry("TEST_BOT_NOT_IN_ROOM", "Это не бот вашей тестовой комнаты."),
            Map.entry("TOKEN_IDENTITY_FORBIDDEN", "Токен можно взять только за тестового бота."),
            Map.entry("PLAYER_NOT_IN_ROOM", "Участника нет в этой комнате."),
            // Озвучка «облака мыслей». Синтезатор речи чужой и неофициальный:
            // когда он молчит, это поломка сервиса, а не ошибка админа, — так
            // текст и написан, чтобы не искали причину у себя.
            Map.entry("TTS_TEXT_REQUIRED", "Нужен текст реплики."),
            Map.entry("TTS_UNAVAILABLE", "Синтез речи временно недоступен."),
            Map.entry("TTS_AUDIO_INVALID", "Синтез речи вернул негодный звук."),
            // Видеосвязь не настроена на сервере — это отвечает 503, и игроку
            // надо сказать про сервер, а не про его действие.
            Map.entry("LIVEKIT_NOT_CONFIGURED", "Видеосвязь временно недоступна."),
            // Сервер видеосвязи не ответил. Игроку это ровно то же самое, что
            // «не настроена», и различаются они только в журнале: разбираться,
            // молчит ли LiveKit или его нет вовсе, — не дело игрока.
            Map.entry("LIVEKIT_UNAVAILABLE", "Видеосвязь временно недоступна."),
            // Область партии /api/v2/game. Движок переехал на сервер, и вместе
            // с ним появились коды, которых у клиентских транзакций быть не
            // могло: там ход подтверждать было не у кого.
            Map.entry("TURN_STALE", "Ход уже сменился. Обновите экран и повторите."),
            Map.entry("TURN_ALREADY_STARTED", "Ход уже начал другой игрок команды."),
            // Текст нарочно не называет действия: одним кодом закрыт весь
            // TurnController — начало хода, слово, пропуск и завершение, — и
            // прежнее «Начать ход может только…» врало тому, кто нажал «угадал».
            Map.entry("TURN_NOT_YOURS", "Это действие доступно только игроку активной команды."),
            Map.entry("NOT_ENOUGH_WORDS", "В шляпе меньше 5 слов: начинать партию рано."),
            Map.entry("WORDS_LOCKED", "Слова можно сдавать только до начала партии."),
            Map.entry("LOADOUT_LOCKED", "Обойму целиком можно менять только между партиями."),
            Map.entry("LOADOUT_REQUIRED", "Сначала зарядите 5 мемов."),
            Map.entry("WORD_NOT_IN_TURN", "Этого слова в разбираемом ходе не было."),
            // Коды, которые бросал ещё старый /api/game, но текста к ним не
            // было ни там, ни здесь: игрок читал на экране саму строку кода.
            Map.entry("EXPLAINER_MISSING", "В этом ходе ещё нет объясняющего."),
            Map.entry("WEAPON_INVALID", "Это оружие сейчас недоступно."),
            // Область комнаты /api/v2/room. Правила, переехавшие из браузера,
            // впервые получают отказ с сервера: раньше их нарушение вообще не
            // доезжало до пользователя — клиент просто не давал нажать кнопку,
            // а мимо кнопки писал документ напрямую.
            Map.entry("SPECTATOR_ONLY", "Это действие доступно только зрителям комнаты."),
            Map.entry("ROOM_CLOSED", "Комната закрыта."),
            Map.entry("ROOM_CLOSED_BY_ADMIN", "Комната закрыта."),
            Map.entry("ROOM_SETUP_ONLY", "Это можно менять только до начала игры."),
            // Отказ хозяйской проверки старого движка (assertRoomHost). Он
            // доезжает до пользователя через v2: POST /api/v2/room/{id}/invites
            // и повышение зрителя зовут этот движок, а не свою формулу. Без
            // строки здесь на экране появлялось само слово ROOM_HOST_ONLY —
            // фронтенд показывает поле error как есть.
            Map.entry("ROOM_HOST_ONLY", "Это действие доступно только администратору комнаты."),
            Map.entry("RANKED_DIVISION_MISMATCH",
                    "Рейтинговая игра доступна только игрокам своего дивизиона."),
            Map.entry("ROOM_DIVISION_MISMATCH",
                    "Эта открытая комната относится к другому языковому дивизиону."),
            Map.entry("PRIVATE_GAME_STARTED",
                    "Эта приватная игра уже началась. Вернуться можно только по своему месту."),
            Map.entry("PRIVATE_ROOM_NOT_WATCHABLE", "Эта комната приватная."),
            Map.entry("SPECTATORS_AFTER_START", "Зрители могут входить только после начала игры."),
            // Составы команд внутри комнаты. Названия кодов начинаются с ROOM_
            // там, где рядом живёт одноимённое понятие рейтинговой команды:
            // «такое название уже занято» про команду в комнате и про команду
            // лиги — разные сообщения разным людям.
            Map.entry("ROOM_TEAM_NOT_FOUND", "Команда не найдена."),
            Map.entry("ROOM_TEAM_NAME_TAKEN", "Команда с таким названием уже есть в этой комнате."),
            Map.entry("TEAM_LIMIT_REACHED", "Можно создать максимум 5 команд по 2 игрока."),
            Map.entry("TEAM_IS_FULL", "В этой команде уже два игрока."),
            Map.entry("TEAM_NOT_EMPTY", "Сначала игроки должны выйти из этой команды."),
            Map.entry("NO_ACTIVE_PLAYERS", "В комнате нет активных игроков."),
            Map.entry("SPECTATOR_NOT_FOUND", "Зритель не найден."),
            // Токен на видео просят за место зрителя, которого у просящего нет.
            // Отдельно от SPECTATOR_NOT_FOUND: там ищут чужое место, здесь — своё.
            Map.entry("SPECTATOR_NOT_IN_ROOM", "Сначала займите место зрителя в этой комнате."),
            // Приглашения в комнату. Отдельно от приглашений в рейтинговую
            // команду: у тех свои коды и свои экраны.
            Map.entry("ROOM_INVITE_NOT_FOUND", "Приглашение не найдено."),
            Map.entry("ROOM_INVITE_FORBIDDEN", "Это приглашение адресовано не вам."),
            Map.entry("ROOM_INVITE_UNAVAILABLE", "Приглашение больше недействительно."),
            // Чат комнаты.
            Map.entry("CHAT_MESSAGE_NOT_FOUND", "Сообщение не найдено."),
            Map.entry("NOT_CHAT_AUTHOR", "Править можно только свои сообщения."),
            Map.entry("CHAT_IMAGE_NOT_EDITABLE", "У сообщения с фотографией нечего править."),
            Map.entry("VALIDATION_FAILED", "Некорректный запрос.")
    );

    public static final Map<String, String> GAME = Map.ofEntries(
            Map.entry("ROOM_INVALID", "Некорректная игровая комната."),
            Map.entry("ROOM_NOT_FOUND", "Комната не найдена."),
            Map.entry("GAME_ALREADY_STARTED", "Игра уже началась."),
            Map.entry("RANKED_ONLY", "Автозапуск доступен только для рейтинговой игры."),
            Map.entry("RANKED_WORDS_UNAVAILABLE", "Не удалось подготовить набор рейтинговых слов."),
            Map.entry("HOST_ONLY", "Это действие доступно только администратору комнаты."),
            Map.entry("OWNER_ONLY", "Это действие доступно только владельцу HOT-HAT."),
            Map.entry("HOST_TRANSFER_TARGET_INVALID", "Выберите другого участника комнаты."),
            Map.entry("PLAYER_NOT_ACTIVE", "Этот участник сейчас неактивен в комнате."),
            Map.entry("KICK_TARGET_REQUIRED", "Не выбран участник для удаления."),
            Map.entry("KICK_SELF_FORBIDDEN", "Администратор не может удалить сам себя этой кнопкой."),
            Map.entry("TEAMS_NOT_READY", "Команды ещё не готовы."),
            Map.entry("ROUND_NOT_ACTIVE", "Сейчас нельзя использовать диверсии."),
            Map.entry("ACTIVE_TEAM_CANNOT_ATTACK", "Активная команда не может атаковать сама себя."),
            Map.entry("WEAPON_NOT_ELIGIBLE", "Использовать арсенал могут только игроки текущей партии."),
            Map.entry("WEAPON_COOLDOWN", "Оружие перезаряжается."),
            Map.entry("NO_AMMO", "Заряды закончились."),
            Map.entry("NOT_ENOUGH_TURN_TIME", "До конца хода осталось слишком мало времени для этой диверсии."),
            Map.entry("VIDEO_EFFECT_BUSY", "На объясняющем уже действует другая видеодиверсия."),
            Map.entry("VOICE_EFFECT_BUSY", "На объясняющем уже действует другая голосовая диверсия."),
            Map.entry("OVERLAY_EFFECT_BUSY", "Сейчас уже действует Объект, Какахи или Мега текст. Дождитесь окончания эффекта."),
            Map.entry("REPLACEMENT_ACTIVE", "Сейчас идёт Подмена. Во время неё разрешены только помидоры и мемы."),
            Map.entry("REPLACEMENT_CONFLICT", "Подмену можно запустить только после окончания активных видео/голосовых эффектов."),
            Map.entry("REPLACEMENT_ALREADY_USED", "В этом ходу Подмена уже была использована."),
            Map.entry("REPLACEMENT_SAME_TURN", "Записанную Подмену можно применить только в следующем ходе этого игрока."),
            Map.entry("REPLACEMENT_WRONG_TARGET", "Записанный игрок сейчас не в активной команде."),
            Map.entry("REPLACEMENT_CLIP_INVALID", "Запись Подмены больше недоступна."),
            Map.entry("REPLACEMENT_NOT_READY", "Запись Подмены ещё не готова."),
            Map.entry("REPLACEMENT_RECORD_LIMIT", "Все 3 слота записей Подмены уже заняты."),
            Map.entry("REPLACEMENT_RECORD_BUSY", "Сейчас уже идёт другая запись Подмены. Дождитесь её окончания."),
            Map.entry("GAME_PAUSED", "Игра на паузе до возвращения игрока."),
            Map.entry("NOT_ENOUGH_TEAM_SLOTS", "Свободных мест в созданных командах недостаточно."),
            Map.entry("MEME_NOT_LOADED", "Этот мем не заряжен."),
            Map.entry("MEME_ALREADY_USED", "Этот мем сейчас недоступен."),
            Map.entry("MEME_IN_RESERVE", "Этот мем сейчас в очереди и вернётся после следующего бонуса за 3 слова."),
            Map.entry("MEME_SLOT_INVALID", "Некорректный слот мема."),
            Map.entry("MEME_DUPLICATE", "Этот мем уже заряжен в другом слоте."),
            Map.entry("ACTIVE_TEAM_LOADOUT_LOCKED", "Активная команда не может менять мемы до окончания своего хода."),
            Map.entry("APPEAL_NOT_ACTIVE", "Апелляция сейчас не идёт."),
            Map.entry("APPEAL_CLOSED", "Время голосования закончилось."),
            Map.entry("APPEAL_NOT_ELIGIBLE", "Активная команда не участвует в голосовании."),
            Map.entry("APPEAL_STILL_OPEN", "Голосование ещё не завершено."),
            Map.entry("DEFAULT_LOADOUT_REQUIRED", "Сначала зарядите 5 мемов в разделе «Мем-арсенал»."),
            Map.entry("ROOM_INVITE_GAME_STARTED", "Это приглашение больше недействительно: игра уже началась или была сыграна."),
            Map.entry("ROOM_INVITE_EXPIRED", "Срок действия приглашения истёк."),
            // Видео-чат: созвон с друзьями без игры. Тексты видит человек на
            // странице созвона и на карточке приглашения.
            Map.entry("CONFERENCE_NOT_FOUND", "Видео-чат не найден."),
            Map.entry("CONFERENCE_CLOSED", "Видео-чат завершён."),
            Map.entry("CONFERENCE_INVITE_REQUIRED", "В этот видео-чат можно войти только по приглашению."),
            Map.entry("CONFERENCE_HOST_ONLY", "Это может сделать только создатель видео-чата."),
            Map.entry("CONFERENCE_HOST_PROTECTED", "Создателя видео-чата нельзя удалить из звонка."),
            Map.entry("CONFERENCE_SELF_INVITE", "Себя приглашать не нужно."),
            Map.entry("CONFERENCE_FULL", "В видео-чате максимум 16 участников."),
            Map.entry("CONFERENCE_INVITE_NOT_FOUND", "Приглашение не найдено или уже отвечено."),
            Map.entry("CONFERENCE_PARTICIPANT_NOT_FOUND", "Такого участника в видео-чате нет."),
            Map.entry("CONFERENCE_GAME_TOO_MANY",
                    "В игровой комнате максимум 10 игроков. Сначала оставьте в видео-чате не больше 10 человек."),
            Map.entry("CONFERENCE_CHAT_EMPTY", "Пустое сообщение отправить нельзя."),
            Map.entry("CONFERENCE_FILE_INVALID", "Файл не из этого видео-чата.")
    );

    public static String resolve(String code, int status) {
        if (code != null && code.startsWith("LOADOUT_REQUIRED:")) {
            String name = code.substring("LOADOUT_REQUIRED:".length());
            return "Игрок «" + (name.isBlank() ? "Игрок" : name) + "» должен выбрать 5 мемов.";
        }
        if (code != null && code.startsWith("FEATURE_DISABLED:")) {
            return BY_CODE.get("FEATURE_DISABLED");
        }
        // Имя поля в хвосте кода нужно разработчику, а не игроку: ему хватает
        // того, что поле служебное. Само имя остаётся в коде ответа.
        if (code != null && code.startsWith("FIELD_FORBIDDEN:")) {
            return BY_CODE.get("FIELD_FORBIDDEN");
        }
        String message = BY_CODE.get(code);
        if (message == null) {
            message = GAME.get(code);
        }
        if (message != null) {
            return message;
        }
        return status >= 500 ? "Сервер временно недоступен." : code;
    }

    public static String publicCode(String code) {
        if (code != null && code.startsWith("FEATURE_DISABLED:")) {
            return "FEATURE_DISABLED";
        }
        if (code != null && code.startsWith("FIELD_FORBIDDEN:")) {
            return "FIELD_FORBIDDEN";
        }
        return code != null && code.startsWith("LOADOUT_REQUIRED:") ? "LOADOUT_REQUIRED" : code;
    }
}
