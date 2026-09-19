package ru.hothat.auth.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;
import ru.hothat.config.HotHatProperties;
import ru.hothat.auth.spi.AccessTokenPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Единственная дверь области {@code auth} в свои таблицы.
 *
 * <p>Наружу отдаёт записи, а не сущности: четыре класса {@code auth.store} не
 * публичны и в сигнатуры сценариев не попадают. Здесь же живёт перевод
 * uid ↔ uuid, поэтому сценарии продолжают говорить тем идентификатором,
 * который стоит в ответах и в чужих таблицах.
 *
 * <p><b>Пароль не покидает этот класс.</b> Ни {@link Account}, ни любая другая
 * запись не несёт хеша: сверка пароля объявлена здесь методом
 * ({@link #authenticate}, {@link #passwordMatches}) ровно ради этого. Отдать
 * хеш наружу «на одну строчку сравнения» — и он окажется в логе, в снимке, в
 * ответе; находка B4 аудита ровно об этом. Ценой оказывается {@link
 * PasswordEncoder} внутри хранилища, и это дешевле.
 *
 * <p>Ни одно чтение не ходит в базу в цикле: учётки списка и права к ним —
 * два запроса на страницу любой длины.
 */
@Component
@RequiredArgsConstructor
public class IdentityStore {

    /** Роды учётки и права; наборы закрыты ограничениями базы. */
    public static final String ROLE_ADMIN = AccountRole.ADMIN;
    public static final String ROLE_OWNER = AccountRole.OWNER;

    /** Причины отзыва refresh-токена. Свободный набор: это журнал, не состояние. */
    public static final String REASON_ROTATED = "rotated";
    public static final String REASON_LOGOUT = "logout";
    public static final String REASON_LOGOUT_ALL = "logout_all";
    public static final String REASON_BAN = "ban";
    public static final String REASON_REUSE = "reuse";
    public static final String REASON_PASSWORD = "password_change";

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Ссылка восстановления живёт час: столько же, сколько жила у старого движка. */
    private static final long RESET_TTL_SECONDS = 3600;

    private final UserAccounts accounts;
    private final AccountRoles roles;
    private final SessionTokens sessions;
    private final PasswordResetTickets resets;
    private final PasswordEncoder passwordEncoder;
    private final HotHatProperties properties;
    /** Только ради срока жизни refresh-токена: он объявлен там же, где access. */
    private final AccessTokenPort jwt;
    private final LegacyIdBridge ids;

    // ───────────────────────────── чтение ─────────────────────────────

    /** Учётка по uid; пусто — такой нет. */
    public Optional<Account> byUid(String uid) {
        if (blank(uid)) {
            return Optional.empty();
        }
        UUID playerId = ids.playerId(uid);
        return accounts.findById(playerId).map(row -> view(row, uid, rolesOf(playerId)));
    }

    /** Учётка по почте без учёта регистра; ею живёт вход. */
    public Optional<Account> byEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        // Учётка без строки моста — это игрок, которого нельзя назвать тем
        // идентификатором, каким его знают комнаты, рейтинги и сам фронтенд.
        // Выдать ему сессию нечем, поэтому такой учётки для входа не
        // существует. Мост заполняет миграция V14 и пишущие сценарии.
        return accounts.findFirstByEmailIgnoreCase(normalized)
                .map(this::view)
                .filter(account -> account.uid() != null);
    }

    /**
     * Учётки названных игроков разом. Пустой список в базу не идёт; игроки,
     * которых нет, в карте отсутствуют — молчаливая подстановка пустой учётки
     * нарисовала бы в реестре строку без человека.
     */
    public Map<String, Account> byUids(Collection<String> uids) {
        List<String> wanted = distinct(uids);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byPlayerId = new LinkedHashMap<>();
        for (String uid : wanted) {
            byPlayerId.put(ids.playerId(uid), uid);
        }
        List<UserAccount> rows = accounts.findByPlayerIdIn(byPlayerId.keySet());
        Map<UUID, Set<String>> grants = rolesOf(byPlayerId.keySet());
        Map<String, Account> result = new LinkedHashMap<>();
        for (UserAccount row : rows) {
            String uid = byPlayerId.get(row.getPlayerId());
            result.put(uid, view(row, uid, grants.getOrDefault(row.getPlayerId(), Set.of())));
        }
        return result;
    }

    /** Полноценные учётки, новые сверху: реестр владельца. Предел уезжает в базу. */
    public List<Account> members(int limit) {
        List<UserAccount> rows = accounts.findByKindOrderByMemberSinceDesc(
                UserAccount.MEMBER, Limit.of(Math.max(1, limit)));
        List<UUID> playerIds = new ArrayList<>(rows.size());
        for (UserAccount row : rows) {
            playerIds.add(row.getPlayerId());
        }
        Map<UUID, String> uids = ids.playerUids(playerIds);
        Map<UUID, Set<String>> grants = rolesOf(playerIds);
        List<Account> result = new ArrayList<>(rows.size());
        for (UserAccount row : rows) {
            String uid = uids.get(row.getPlayerId());
            if (uid == null) {
                // Учётка без строки моста — это игрок, которого нельзя назвать
                // тем идентификатором, каким его знают комнаты и рейтинги.
                // Показать его в реестре нечем.
                continue;
            }
            result.add(view(row, uid, grants.getOrDefault(row.getPlayerId(), Set.of())));
        }
        return result;
    }

    /**
     * Владелец сервиса: сперва строка права {@code OWNER}, и только если её
     * ещё нет — учётка с настроенной почтой. Порядок именно такой: право
     * записано строкой и переживает смену почты, а настройка — загрузчик.
     */
    public Optional<String> ownerUid() {
        for (AccountRole role : roles.findByRole(ROLE_OWNER)) {
            String uid = uidOf(role.getPlayerId());
            if (uid != null) {
                return Optional.of(uid);
            }
        }
        return byEmail(properties.owner()).map(Account::uid);
    }

    public long countAccounts() {
        return accounts.count();
    }

    /** Сколько человек зарегистрировалось за период; считает база. */
    public long countMembersRegisteredBetween(Instant from, Instant to) {
        return accounts.countByKindAndMemberSinceBetween(UserAccount.MEMBER, from, to);
    }

    /** Свободна ли почта. Отдельный вопрос от «кто её занял»: ответ наружу не едет. */
    public boolean emailTaken(String email) {
        return byEmail(email).isPresent();
    }

    // ───────────────────────────── пароль ─────────────────────────────

    /**
     * Проверить пару «почта + пароль». Пусто — адреса нет, пароля нет либо он
     * не подошёл: три случая намеренно неразличимы, иначе форма входа
     * превращается в перечислитель зарегистрированных почт.
     */
    public Optional<Account> authenticate(String email, String rawPassword) {
        Optional<UserAccount> found = accounts.findFirstByEmailIgnoreCase(normalizeEmail(email));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserAccount row = found.get();
        if (!matches(row, rawPassword)) {
            return Optional.empty();
        }
        return Optional.of(view(row));
    }

    /** Подтверждение личности своим же паролем: смена пароля спрашивает текущий. */
    public boolean passwordMatches(String uid, String rawPassword) {
        return accounts.findById(ids.playerId(uid))
                .map(row -> matches(row, rawPassword))
                .orElse(false);
    }

    // ───────────────────────────── запись ─────────────────────────────

    /**
     * Завести гостя. Ни почты, ни пароля у него нет — это держит ограничение
     * базы, а не соглашение.
     */
    public Account openGuest() {
        String uid = newUid();
        UUID playerId = ids.playerId(uid);
        ids.rememberPlayers(List.of(uid));
        UserAccount row = accounts.saveAndFlush(UserAccount.builder()
                .playerId(playerId)
                .kind(UserAccount.GUEST)
                .build());
        return view(row, uid, Set.of());
    }

    /** Завести полноценную учётку. Почта уже проверена вызывающим на занятость. */
    public Account openMember(String email, String rawPassword) {
        String uid = newUid();
        UUID playerId = ids.playerId(uid);
        ids.rememberPlayers(List.of(uid));
        Instant now = Instant.now();
        UserAccount row = accounts.saveAndFlush(UserAccount.builder()
                .playerId(playerId)
                .kind(UserAccount.MEMBER)
                .email(normalizeEmail(email))
                .passwordHash(passwordEncoder.encode(rawPassword))
                .memberSince(now)
                .passwordUpdatedAt(now)
                .build());
        grantConfiguredRoles(playerId, row.getEmail(), uid);
        return view(row, uid, rolesOf(playerId));
    }

    /**
     * Превратить гостя в участника, сохранив uid и всю статистику.
     *
     * @return {@code false} — учётки нет либо она уже не гостевая; второй ответ
     *         даёт условие в самом {@code UPDATE}, а не чтение перед записью
     */
    public boolean upgradeGuest(String uid, String email, String rawPassword) {
        UUID playerId = ids.playerId(uid);
        int changed = accounts.upgradeGuest(playerId, normalizeEmail(email),
                passwordEncoder.encode(rawPassword), Instant.now());
        if (changed == 0) {
            return false;
        }
        grantConfiguredRoles(playerId, normalizeEmail(email), uid);
        return true;
    }

    /**
     * Задать новый пароль. Тем же оператором поднимается поколение токенов, а
     * следом гасятся живые refresh: смена пароля обязана выкидывать чужие
     * сессии, иначе она не защищает ни от чего.
     */
    public void replacePassword(String uid, String rawPassword) {
        UUID playerId = ids.playerId(uid);
        accounts.replacePassword(playerId, passwordEncoder.encode(rawPassword), Instant.now());
        sessions.revokeAllOfPlayer(playerId, Instant.now(), REASON_PASSWORD);
    }

    /**
     * Отозвать доступ немедленно: поднять поколение и погасить живые
     * refresh-токены. Одним вызовом, потому что порознь они бесполезны —
     * поднятое поколение без гашения оставляет обмен работающим, а гашение
     * без поколения оставляет живой access ещё на пятнадцать минут.
     */
    public void revokeAccess(String uid, String reason) {
        UUID playerId = ids.playerId(uid);
        accounts.bumpTokenVersion(playerId);
        sessions.revokeAllOfPlayer(playerId, Instant.now(), reason);
    }

    // ───────────────────────────── права ─────────────────────────────

    /**
     * Права игрока: строки {@code account_role} плюс то, что называет
     * конфигурация.
     *
     * <p>Конфигурация осталась в формуле намеренно и ровно как загрузчик:
     * таблица прав пуста в день выкатки, и без неё у сервиса не оказалось бы
     * ни одного администратора — включая того, кто должен раздать права.
     * Строки заводятся сами при входе и регистрации ({@link
     * #grantConfiguredRoles}), поэтому список в настройках со временем
     * становится ненужным, а формула всё это время остаётся одна.
     */
    public Set<String> rolesOf(String uid, String email) {
        Set<String> granted = new HashSet<>(rolesOf(ids.playerId(uid)));
        if (properties.adminUids().contains(uid)
                || (email != null && properties.adminEmails().contains(email.toLowerCase(Locale.ROOT)))) {
            granted.add(ROLE_ADMIN);
        }
        if (properties.isOwnerEmail(email)) {
            granted.add(ROLE_OWNER);
            granted.add(ROLE_ADMIN);
        }
        return granted;
    }

    /** Выдать право явно: сюда однажды придёт консоль владельца. */
    public void grantRole(String uid, String role, String grantedByUid) {
        UUID playerId = ids.playerId(uid);
        AccountRoleId key = new AccountRoleId(playerId, role);
        if (roles.existsById(key)) {
            return;
        }
        roles.save(AccountRole.builder()
                .playerId(playerId)
                .role(role)
                .grantedBy(blank(grantedByUid) ? null : ids.playerId(grantedByUid))
                .build());
    }

    // ───────────────────────────── сессии ─────────────────────────────

    /**
     * Выдать refresh-токен новой цепочки. Возвращается сама строка: в базе
     * лежит только её хеш, поэтому утечка таблицы не даёт войти ни за кого.
     */
    public String openSession(String uid, String userAgent, String ip) {
        return storeRefresh(uid, UUID.randomUUID(), userAgent, ip);
    }

    /**
     * Обменять предъявленный токен на новый в той же цепочке.
     *
     * <p>Прежний гасится и получает водяной знак {@code replaced_by}: по нему
     * потом видно, что именно из чего выросло, когда придётся разбирать кражу.
     */
    public String rotateSession(String rawToken, String userAgent, String ip) {
        byte[] hash = sha256(rawToken);
        SessionToken stored = sessions.findByTokenHash(hash).orElseThrow();
        String issued = storeRefresh(uidOf(stored.getPlayerId()), stored.getFamilyId(), userAgent, ip);
        stored.setRevokedAt(Instant.now());
        stored.setRevokedReason(REASON_ROTATED);
        stored.setReplacedBy(sha256(issued));
        sessions.save(stored);
        return issued;
    }

    /** Предъявленный refresh-токен так, как его видит сценарий обмена. */
    public Optional<RefreshRow> findSession(String rawToken) {
        if (blank(rawToken)) {
            return Optional.empty();
        }
        return sessions.findByTokenHash(sha256(rawToken)).map(row -> new RefreshRow(
                uidOf(row.getPlayerId()),
                row.getFamilyId(),
                row.getRevokedAt() != null,
                row.usable(Instant.now())));
    }

    /** Погасить один токен: выход с этого устройства. */
    public void closeSession(String rawToken, String reason) {
        if (blank(rawToken)) {
            return;
        }
        sessions.findByTokenHash(sha256(rawToken)).ifPresent(row -> {
            if (row.getRevokedAt() != null) {
                return;
            }
            row.setRevokedAt(Instant.now());
            row.setRevokedReason(reason);
            sessions.save(row);
        });
    }

    /** Погасить всю цепочку ротаций — ответ на реюз (A11). */
    public void closeFamily(UUID familyId, String reason) {
        sessions.revokeFamily(familyId, Instant.now(), reason);
    }

    /** Плановая уборка обеих таблиц токенов. */
    public int sweepExpired(Instant before) {
        return sessions.deleteExpired(before) + resets.deleteExpired(before);
    }

    // ─────────────────────── восстановление пароля ───────────────────────

    /**
     * Выпустить одноразовую ссылку. Прежние заявки того же игрока при этом
     * обесцениваются: две живые ссылки на один аккаунт — это две двери.
     */
    public String openPasswordReset(String uid, String ip) {
        UUID playerId = ids.playerId(uid);
        Instant now = Instant.now();
        resets.invalidateAllOfPlayer(playerId, now);
        String token = randomToken();
        resets.save(PasswordResetTicket.builder()
                .tokenHash(sha256(token))
                .playerId(playerId)
                .expiresAt(now.plusSeconds(RESET_TTL_SECONDS))
                .requestedIp(trim(ip, 45))
                .build());
        return token;
    }

    /**
     * Применить ссылку: пометить использованной и назвать владельца.
     *
     * <p>Пометка идёт условным {@code UPDATE}, поэтому два одновременных
     * перехода по одной ссылке меняют пароль один раз, а не два.
     *
     * @return uid владельца; пусто — ссылки нет, она истекла или её уже применили
     */
    public Optional<String> consumePasswordReset(String rawToken) {
        if (blank(rawToken)) {
            return Optional.empty();
        }
        byte[] hash = sha256(rawToken);
        PasswordResetTicket ticket = resets.findByTokenHash(hash).orElse(null);
        if (ticket == null || !ticket.usable(Instant.now())) {
            return Optional.empty();
        }
        if (resets.markUsed(hash, Instant.now()) == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(uidOf(ticket.getPlayerId()));
    }

    // ───────────────────────────── проекции ─────────────────────────────

    /**
     * Учётка так, как её видит сценарий: без хеша пароля и без счётчика
     * отзыва в качестве значения — есть пароль или нет, и какое сейчас
     * поколение, сказано отдельными полями.
     */
    public record Account(String uid, String email, boolean guest, int tokenVersion,
                          boolean passwordSet, Long memberSinceMs, Long passwordUpdatedAtMs,
                          boolean admin, boolean owner) {
    }

    /** Предъявленный refresh-токен: чей он, из какой цепочки и годен ли. */
    public record RefreshRow(String uid, UUID familyId, boolean revoked, boolean usable) {
    }

    // ───────────────────────────── внутреннее ─────────────────────────────

    private String storeRefresh(String uid, UUID familyId, String userAgent, String ip) {
        String token = randomToken();
        sessions.save(SessionToken.builder()
                .tokenHash(sha256(token))
                .playerId(ids.playerId(uid))
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(jwt.refreshTtlSeconds()))
                .createdIp(trim(ip, 45))
                .userAgent(trim(userAgent, 200))
                .build());
        return token;
    }

    private boolean matches(UserAccount row, String rawPassword) {
        // У гостя пароля нет вовсе: он входит только своим refresh-токеном.
        // Сравнение всё равно выполняется — иначе по времени ответа видно,
        // заведён ли адрес.
        String hash = row.getPasswordHash();
        if (hash == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword == null ? "" : rawPassword, hash);
    }

    private Account view(UserAccount row) {
        String uid = uidOf(row.getPlayerId());
        return view(row, uid, rolesOf(row.getPlayerId()));
    }

    private Account view(UserAccount row, String uid, Set<String> granted) {
        boolean guest = UserAccount.GUEST.equals(row.getKind());
        Set<String> effective = new HashSet<>(granted);
        if (!guest) {
            // Конфигурация — загрузчик прав, см. rolesOf(String, String).
            if (properties.adminUids().contains(uid)
                    || (row.getEmail() != null
                        && properties.adminEmails().contains(row.getEmail().toLowerCase(Locale.ROOT)))) {
                effective.add(ROLE_ADMIN);
            }
            if (properties.isOwnerEmail(row.getEmail())) {
                effective.add(ROLE_OWNER);
                effective.add(ROLE_ADMIN);
            }
        }
        return new Account(
                uid,
                row.getEmail(),
                guest,
                row.getTokenVersion() == null ? 0 : row.getTokenVersion(),
                row.getPasswordHash() != null,
                millis(row.getMemberSince()),
                millis(row.getPasswordUpdatedAt()),
                // Гость не может быть ни администратором, ни владельцем, и это
                // решается здесь, а не доверяется спискам из настроек: uid гостя
                // выдаёт сервер, и опечатка в admin-uids не должна превращать
                // случайного гостя в администратора.
                !guest && effective.contains(ROLE_ADMIN),
                !guest && effective.contains(ROLE_OWNER));
    }

    private Set<String> rolesOf(UUID playerId) {
        Set<String> granted = new HashSet<>();
        for (AccountRole role : roles.findByPlayerId(playerId)) {
            granted.add(role.getRole());
        }
        return granted;
    }

    private Map<UUID, Set<String>> rolesOf(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> byPlayer = new HashMap<>();
        for (AccountRole role : roles.findByPlayerIdIn(playerIds)) {
            byPlayer.computeIfAbsent(role.getPlayerId(), key -> new HashSet<>()).add(role.getRole());
        }
        return byPlayer;
    }

    /**
     * Записать строкой то, что сегодня названо только в настройках. Зовётся
     * при заведении и апгрейде учётки: со временем таблица наполняется сама,
     * и списки в конфигурации перестают быть единственным ответом.
     */
    private void grantConfiguredRoles(UUID playerId, String email, String uid) {
        if (properties.adminUids().contains(uid)
                || (email != null && properties.adminEmails().contains(email.toLowerCase(Locale.ROOT)))) {
            saveRole(playerId, ROLE_ADMIN);
        }
        if (properties.isOwnerEmail(email)) {
            // Владелец записан строкой ровно один — это держит частичный
            // уникальный индекс ux_account_role_single_owner. Настройка здесь
            // загрузчик, а не хозяин: если строка OWNER уже есть у другого
            // игрока, она побеждает — тот же порядок, что и в ownerUid().
            //
            // Без этой проверки saveRole спрашивал «есть ли OWNER у ЭТОГО
            // игрока», а индекс запрещает «OWNER у кого угодно ещё», и вставка
            // падала нарушением ограничения. Роняла она не выдачу права, а всю
            // транзакцию регистрации: настроенный владелец не мог ни завести
            // учётку, ни поднять гостя — сервер отвечал 409 «Данные
            // изменились: повторите попытку», и повтор не помогал никогда.
            // Проверено вживую: строка OWNER осталась за учёткой прежнего
            // владельца, и регистрация на настроенную почту падала всегда.
            //
            // Права настроенный владелец при этом не теряет: admin и owner
            // считаются объединением строк и настройки (см. view), поэтому
            // консоль ему доступна и без своей строки.
            if (roles.findByRole(ROLE_OWNER).isEmpty()) {
                saveRole(playerId, ROLE_OWNER);
            }
            saveRole(playerId, ROLE_ADMIN);
        }
    }

    private void saveRole(UUID playerId, String role) {
        if (roles.existsById(new AccountRoleId(playerId, role))) {
            return;
        }
        roles.save(AccountRole.builder().playerId(playerId).role(role).build());
    }

    private String uidOf(UUID playerId) {
        return ids.playerUids(List.of(playerId)).get(playerId);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static Long millis(Instant moment) {
        return moment == null ? null : moment.toEpochMilli();
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
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * uid той же формы, что раньше выдавал Firebase (28 символов base62): его
     * длину и алфавит уже закладывают комнаты, чаты и ключи рейтингов. Форма
     * не меняется вместе с переездом намеренно — иначе сменился бы ключ в
     * доброй половине таблиц, которые ещё не переехали.
     */
    private static String newUid() {
        byte[] bytes = new byte[21];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                .replace('-', 'a').replace('_', 'b').substring(0, 28);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Тридцать два байта, а не шестьдесят четыре символа шестнадцатеричной
     * записи: по хешу никто не ищет глазами, а места в таблице и в индексе он
     * занимал вдвое больше.
     */
    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
