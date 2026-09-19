package ru.hothat.profile.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;
import ru.hothat.config.ApiException;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;
import ru.hothat.util.Latin;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Единственная дверь области {@code profile} в свои таблицы.
 *
 * <p>Наружу отдаёт записи, а не сущности: пять классов {@code profile.store}
 * не публичны и в сигнатуры сценариев не попадают. Здесь же живёт перевод
 * uid ↔ uuid и перевод аватара data-URL ↔ (тип, байты): контракт обещает
 * data-URL, а таблица хранит двоичное тело, и знание об этом стыке не должно
 * растекаться по сценариям.
 *
 * <p>Ни одно чтение не ходит в базу в цикле: карточки списка — это три
 * запроса (карточки, аватары, присутствие) на сколько угодно игроков.
 *
 * <p>Ников больше не два. Раньше имя лежало и в {@code app_user.nickname}, и
 * в {@code app_user.display_name}, и третьей копией в {@code nickname_index};
 * лестница «ник, иначе отображаемое имя, иначе индекс, иначе сгенерированное»
 * повторялась в шести местах. Здесь имя одно, а уникальность держит
 * вычисляемый ключ базы.
 */
@Component
@RequiredArgsConstructor
public class ProfileStore {

    /** Шесть документов контракта: имя в теле запроса → имя в таблице. */
    private static final Map<String, String> DOCUMENTS = Map.of(
            "agreement", PlayerConsent.AGREEMENT,
            "privacy", PlayerConsent.PRIVACY,
            "personalData", PlayerConsent.PERSONAL_DATA,
            "community", PlayerConsent.COMMUNITY,
            "recording", PlayerConsent.RECORDING,
            "divisions", PlayerConsent.DIVISIONS);

    /** Тот же порядок, что у контракта: по нему собирается ответ о согласиях. */
    private static final List<String> DOCUMENT_ORDER =
            List.of("agreement", "privacy", "personalData", "community", "recording", "divisions");

    private static final Pattern AVATAR =
            Pattern.compile("^data:(image/(?:webp|jpeg|png));base64,(.+)$", Pattern.CASE_INSENSITIVE);

    /** 120 000 символов base64 — тот же предел, что стоял на входе и раньше. */
    private static final int AVATAR_MAX_CHARS = 120_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlayerProfiles profiles;
    private final PlayerAvatars avatars;
    private final PlayerPresences presences;
    private final PlayerConsents consents;
    private final NicknameRequests nicknameRequests;
    private final LegacyIdBridge ids;

    // ───────────────────────────── чтение ─────────────────────────────

    /** Карточка игрока; пусто — карточки нет. */
    public Optional<Card> card(String uid) {
        if (blank(uid)) {
            return Optional.empty();
        }
        return Optional.ofNullable(cards(List.of(uid)).get(uid));
    }

    /**
     * Карточки названных игроков разом. Три запроса на любой список; игроков
     * без карточки в ответе нет — молчаливая заглушка нарисовала бы в списке
     * друзей строку без человека.
     */
    public Map<String, Card> cards(Collection<String> uids) {
        List<String> wanted = distinct(uids);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byPlayerId = new LinkedHashMap<>();
        for (String uid : wanted) {
            byPlayerId.put(ids.playerId(uid), uid);
        }
        Set<UUID> keys = byPlayerId.keySet();
        Map<UUID, String> avatarUrls = new HashMap<>();
        for (PlayerAvatar avatar : avatars.findByPlayerIdIn(keys)) {
            avatarUrls.put(avatar.getPlayerId(), dataUrl(avatar));
        }
        Map<UUID, PlayerPresence> presenceRows = new HashMap<>();
        for (PlayerPresence presence : presences.findByPlayerIdIn(keys)) {
            presenceRows.put(presence.getPlayerId(), presence);
        }
        Map<String, Card> result = new LinkedHashMap<>();
        for (PlayerProfile profile : profiles.findByPlayerIdIn(keys)) {
            String uid = byPlayerId.get(profile.getPlayerId());
            PlayerPresence presence = presenceRows.get(profile.getPlayerId());
            result.put(uid, new Card(
                    uid,
                    profile.getNickname(),
                    avatarUrls.get(profile.getPlayerId()),
                    Divisions.normalize(profile.getDivisionLanguage()),
                    Divisions.allowedUiLanguage(profile.getDivisionLanguage(), profile.getUiLanguage()),
                    profile.getDivisionLockedAt() != null,
                    profile.getAdultConfirmedAt() != null,
                    presence == null || presence.getLastSeenAt() == null
                            ? 0L : presence.getLastSeenAt().toEpochMilli(),
                    presence == null ? null : presence.getActiveRoomId()));
        }
        return result;
    }

    /** Чья это карточка; пусто — ник свободен либо непригоден по форме. */
    public Optional<String> uidByNickname(String nickname) {
        String clean = nickname == null ? "" : nickname.trim();
        if (!Ids.NICKNAME.matcher(clean).matches()) {
            return Optional.empty();
        }
        return profiles.findByNicknameKey(key(clean))
                .map(profile -> ids.playerUids(List.of(profile.getPlayerId())).get(profile.getPlayerId()));
    }

    /** Занят ли ник. Отдельный вопрос от «кем занят»: ответ наружу не едет. */
    public boolean nicknameTaken(String nickname) {
        String clean = nickname == null ? "" : nickname.trim();
        return !clean.isEmpty() && profiles.existsByNicknameKey(key(clean));
    }

    /** Сколько игроков отмечалось начиная с этого мгновения. */
    public long onlineSince(Instant since) {
        return presences.countByLastSeenAtGreaterThanEqual(since);
    }

    /** Принятые документы игрока: последняя принятая версия каждого. */
    public Consents consents(String uid) {
        UUID playerId = ids.playerId(uid);
        Map<String, String> versions = new LinkedHashMap<>();
        Instant latest = null;
        for (PlayerConsent row : consents.findByPlayerIdOrderByAcceptedAtDesc(playerId)) {
            String contractKey = contractKey(row.getDocument());
            if (contractKey != null && !versions.containsKey(contractKey)) {
                versions.put(contractKey, row.getDocumentVersion());
            }
            if (latest == null || (row.getAcceptedAt() != null && row.getAcceptedAt().isAfter(latest))) {
                latest = row.getAcceptedAt();
            }
        }
        boolean adult = profiles.findById(playerId)
                .map(profile -> profile.getAdultConfirmedAt() != null)
                .orElse(false);
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String contractKey : DOCUMENT_ORDER) {
            ordered.put(contractKey, versions.get(contractKey));
        }
        return new Consents(!versions.isEmpty(), adult,
                latest == null ? null : latest.toEpochMilli(), ordered);
    }

    // ───────────────────────────── запись ─────────────────────────────

    /**
     * Завести карточку. Ник обязателен: карточка без имени была тем самым
     * состоянием, ради которого существовала лестница разрешения ника.
     *
     * <p>Дивизион при заведении не закрепляется — его выбирает игрок на
     * онбординге. Раньше первое же чтение профиля проставляло
     * {@code division_locked_at}, и новичок оказывался навсегда в русском
     * дивизионе, не выбрав ничего.
     */
    public Card createCard(String uid, String nickname, String divisionLanguage) {
        ids.rememberPlayers(List.of(uid));
        UUID playerId = ids.playerId(uid);
        String division = Divisions.normalize(divisionLanguage);
        profiles.saveAndFlush(PlayerProfile.builder()
                .playerId(playerId)
                .nickname(requireFreeNickname(nickname))
                .divisionLanguage(division)
                .uiLanguage(Divisions.allowedUiLanguage(division, division))
                .build());
        presences.save(PlayerPresence.builder().playerId(playerId).build());
        return card(uid).orElseThrow();
    }

    /**
     * Карточка есть — вернуть; нет — завести с придуманным именем.
     *
     * <p>Существует ради учёток, заведённых до переезда: у них строки в
     * {@code v2.player_profile} нет, а показать их надо. Новые учётки приходят
     * сюда уже с карточкой — её создаёт регистрация одной транзакцией.
     */
    public Card ensureCard(String uid, String nameHint, String divisionLanguage) {
        Optional<Card> existing = card(uid);
        if (existing.isPresent()) {
            return existing.get();
        }
        return createCard(uid, freeNicknameFrom(nameHint, uid), divisionLanguage);
    }

    /**
     * Переименовать. Занятость проверяется здесь ради понятной ошибки, но
     * последнее слово — за уникальным индексом базы: два одновременных
     * переименования в одно имя до него доходили оба.
     */
    public String rename(String uid, String nickname) {
        UUID playerId = ids.playerId(uid);
        String clean = requireNicknameShape(nickname);
        Optional<PlayerProfile> holder = profiles.findByNicknameKey(key(clean));
        if (holder.isPresent() && !holder.get().getPlayerId().equals(playerId)) {
            throw ApiException.of("NICKNAME_TAKEN", 409);
        }
        PlayerProfile profile = profiles.findById(playerId)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        profile.setNickname(clean);
        profiles.saveAndFlush(profile);
        return clean;
    }

    /**
     * Закрепить дивизион. Повтор тем же кодом проходит молча — это не смена, а
     * та же самая заявка второй раз; повтор другим кодом отвечает 409.
     */
    public String lockDivision(String uid, String divisionLanguage) {
        UUID playerId = ids.playerId(uid);
        String division = Divisions.normalize(divisionLanguage, "ru");
        String uiLanguage = Divisions.allowedUiLanguage(division, division);
        if (profiles.lockDivision(playerId, division, uiLanguage, Instant.now()) > 0) {
            return division;
        }
        PlayerProfile profile = profiles.findById(playerId)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        if (!division.equals(Divisions.normalize(profile.getDivisionLanguage()))) {
            throw ApiException.of("DIVISION_LOCKED", 409);
        }
        return division;
    }

    /**
     * Переключить язык интерфейса. Допустим язык своего дивизиона либо
     * английский — правило проверяется здесь же, поэтому «попросил третий»
     * возвращает язык дивизиона, а не ошибку.
     */
    public UiLanguages setUiLanguage(String uid, String uiLanguage) {
        PlayerProfile profile = profiles.findById(ids.playerId(uid))
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        String division = Divisions.normalize(profile.getDivisionLanguage());
        String allowed = Divisions.allowedUiLanguage(division, uiLanguage);
        profile.setUiLanguage(allowed);
        profiles.save(profile);
        return new UiLanguages(allowed, division);
    }

    /**
     * Заменить аватар. Разбор data-URL живёт здесь: таблица хранит тип и
     * байты, и превращать одно в другое должно то же место, что и обратно.
     */
    public String replaceAvatar(String uid, String avatarDataUrl) {
        String value = avatarDataUrl == null ? "" : avatarDataUrl.trim();
        if (value.isEmpty()) {
            throw ApiException.of("AVATAR_REQUIRED", 400);
        }
        Matcher matcher = AVATAR.matcher(value);
        if (!matcher.matches() || value.length() > AVATAR_MAX_CHARS) {
            throw ApiException.of("AVATAR_INVALID", 400);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw ApiException.of("AVATAR_INVALID", 400);
        }
        if (bytes.length == 0 || bytes.length > 92_160) {
            throw ApiException.of("AVATAR_INVALID", 400);
        }
        UUID playerId = ids.playerId(uid);
        avatars.save(PlayerAvatar.builder()
                .playerId(playerId)
                .mediaType(matcher.group(1).toLowerCase(Locale.ROOT))
                .bytes(bytes)
                .sha256(sha256(bytes))
                .updatedAt(Instant.now())
                .build());
        return value;
    }

    /** Отметить, что игрок сейчас в сети. Одиночный UPDATE, без чтения строки. */
    public void touchPresence(String uid) {
        UUID playerId = ids.playerId(uid);
        if (presences.touch(playerId, Instant.now()) == 0) {
            presences.save(PlayerPresence.builder().playerId(playerId).build());
        }
    }

    /**
     * Пометить комнату, в которой игрок сейчас играет; {@code null} — снять
     * метку. Время ставит сервер: браузер писал сюда свои часы, а по ним
     * нельзя отличить свежую метку от забытой в прошлом месяце.
     */
    public long setActiveRoom(String uid, String roomId) {
        UUID playerId = ids.playerId(uid);
        Instant now = Instant.now();
        String room = blank(roomId) ? null : roomId.trim();
        if (presences.setActiveRoom(playerId, room, room == null ? null : now, now) == 0) {
            presences.save(PlayerPresence.builder()
                    .playerId(playerId)
                    .lastSeenAt(now)
                    .activeRoomId(room)
                    .activeRoomAt(room == null ? null : now)
                    .build());
        }
        return now.toEpochMilli();
    }

    /**
     * Записать принятие правовых документов.
     *
     * <p>Строка на документ и повторно не пишется: {@code UNIQUE(player_id,
     * document, document_version)} и есть определение «принял». Совершеннолетие
     * — не документ и версии не имеет, поэтому оно живёт отметкой в карточке.
     */
    public long recordConsents(String uid, Map<String, String> versions, boolean adultConfirmed,
                               String sourceIp, String userAgent) {
        UUID playerId = ids.playerId(uid);
        Instant now = Instant.now();
        for (Map.Entry<String, String> entry : versions.entrySet()) {
            String document = DOCUMENTS.get(entry.getKey());
            String version = entry.getValue() == null ? "" : entry.getValue().trim();
            if (document == null || version.isEmpty()) {
                continue;
            }
            if (consents.existsByPlayerIdAndDocumentAndDocumentVersion(playerId, document, version)) {
                continue;
            }
            consents.save(PlayerConsent.builder()
                    .playerId(playerId)
                    .document(document)
                    .documentVersion(trim(version, 40))
                    .acceptedAt(now)
                    .sourceIp(trim(sourceIp, 45))
                    .userAgent(trim(userAgent, 400))
                    .build());
        }
        if (adultConfirmed) {
            profiles.findById(playerId).ifPresent(profile -> {
                if (profile.getAdultConfirmedAt() == null) {
                    profile.setAdultConfirmedAt(now);
                    profiles.save(profile);
                }
            });
        }
        return now.toEpochMilli();
    }

    /**
     * Подать заявку на занятый ник. Вторая открытая заявка того же игрока не
     * заводится: её и не примет частичный уникальный индекс.
     */
    public void openNicknameRequest(String uid, String currentNickname, String requested, String reason) {
        UUID playerId = ids.playerId(uid);
        if (nicknameRequests.existsByPlayerIdAndStatus(playerId, NicknameRequest.NEW)) {
            return;
        }
        nicknameRequests.save(NicknameRequest.builder()
                .playerId(playerId)
                .currentNickname(currentNickname)
                .requestedNickname(requireNicknameShape(requested))
                .reason(trim(reason, 500))
                .build());
    }

    /**
     * Свободное имя, похожее на подсказку. Гостю подсказки нет вовсе — тогда
     * получается {@code Guest######}, как и раньше.
     */
    public String freeNicknameFrom(String hint, String uid) {
        String source = hint == null ? "" : hint.trim();
        String base = source.isEmpty()
                ? "Guest" + (100000 + RANDOM.nextInt(900000))
                : Latin.nicknameBase(source, uid);
        if (!Ids.NICKNAME.matcher(base).matches()) {
            base = "player" + Latin.tail(uid);
        }
        String candidate = base;
        int attempt = 1;
        while (profiles.existsByNicknameKey(key(candidate))) {
            String prefix = base.length() > 15 ? base.substring(0, 15) : base;
            candidate = prefix + (attempt++);
            if (attempt > 50) {
                throw ApiException.of("NICKNAME_TAKEN", 409);
            }
        }
        return candidate;
    }

    // ───────────────────────────── проекции ─────────────────────────────

    /**
     * Карточка так, как её видят сценарии и соседние области: имя, аватар,
     * дивизион, язык и присутствие. Ни почты, ни хеша пароля, ни счётчика
     * отзыва токенов здесь нет и быть не может — это другая таблица и другая
     * область.
     */
    public record Card(String uid, String nickname, String avatarDataUrl,
                       String divisionLanguage, String uiLanguage,
                       boolean divisionLocked, boolean adultConfirmed,
                       long lastSeenAtMs, String activeRoomId) {
    }

    /** Пара языков: интерфейса и дивизиона — ответ показывает оба. */
    public record UiLanguages(String uiLanguage, String divisionLanguage) {
    }

    /** Принятые документы: последняя версия каждого плюс момент последнего принятия. */
    public record Consents(boolean accepted, boolean adultConfirmed, Long acceptedAtMs,
                           Map<String, String> versions) {
    }

    // ───────────────────────────── внутреннее ─────────────────────────────

    private String requireFreeNickname(String nickname) {
        String clean = requireNicknameShape(nickname);
        if (profiles.existsByNicknameKey(key(clean))) {
            throw ApiException.of("NICKNAME_TAKEN", 409);
        }
        return clean;
    }

    private static String requireNicknameShape(String nickname) {
        String clean = nickname == null ? "" : nickname.trim();
        if (!Ids.NICKNAME.matcher(clean).matches()) {
            throw ApiException.of("INVALID_NICKNAME", 400);
        }
        return clean;
    }

    /** Тот же ключ, что считает база в {@code nickname_key}. */
    private static String key(String nickname) {
        return nickname.trim().toLowerCase(Locale.ROOT);
    }

    private static String contractKey(String document) {
        for (Map.Entry<String, String> entry : DOCUMENTS.entrySet()) {
            if (entry.getValue().equals(document)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String dataUrl(PlayerAvatar avatar) {
        return "data:" + avatar.getMediaType() + ";base64,"
                + Base64.getEncoder().encodeToString(avatar.getBytes());
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> distinct(Collection<String> values) {
        Set<String> wanted = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                wanted.add(value);
            }
        }
        return new ArrayList<>(wanted);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
