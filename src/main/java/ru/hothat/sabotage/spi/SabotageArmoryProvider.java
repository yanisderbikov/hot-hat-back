package ru.hothat.sabotage.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.spi.AccountPort;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatProperties;
import ru.hothat.friend.spi.FriendshipPort;
import ru.hothat.repository.GetterSocial;
import ru.hothat.sabotage.store.SabotageStore;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.WeaponRegistry;
import ru.hothat.game.port.MemeCatalogPort;
import ru.hothat.util.Ids;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Правила снаряжения диверсий. Хранение — у {@link SabotageStore}, решения здесь.
 *
 * <p>Раздел был частью {@code ProfileServiceImpl} и жил колонками
 * {@code app_user}: {@code sabotage_unlimited}, {@code sabotage_games_used},
 * {@code default_meme_loadout} плюс таблица {@code sabotage_game_use} с
 * ключом-склейкой «комната-номер-игрок». Теперь это таблицы {@code v2}, а
 * вместе с ними ушли две ошибки, которые старое хранение делало неизбежными:
 * счётчик израсходованного считался чтением и записью карточки целиком (две
 * вкладки теряли один инкремент), а обойма была массивом, где один мем мог
 * стоять дважды — то есть четыре заряда вместо пяти.
 *
 * <p>Предел бесплатных партий больше не константа: он лежит колонкой
 * {@code free_games_limit}, и раздать одному человеку десять партий можно без
 * выкатки. Число «пять» осталось в коде ровно одно — {@link #LOADOUT_SIZE}
 * у порта этой области, и он же проверяется при чтении.
 */
@Service
@RequiredArgsConstructor
public class SabotageArmoryProvider implements SabotageArmoryPort {

    private final SabotageStore store;
    private final HotHatProperties properties;
    private final AccountPort accounts;
    private final FriendshipPort friendships;
    private final GetterSocial getterSocial;
    private final MemeCatalogPort memes;

    @Override
    @Transactional
    public Entitlement entitlement(String uid, String email) {
        if (properties.isOwnerEmail(email)) {
            return unlimited();
        }
        // Чтение квоты заводит строку права: до первого захода её нет, а
        // предел бесплатных партий задан умолчанием колонки — спрашивать его
        // у базы и есть весь смысл переноса.
        SabotageStore.Quota quota = store.ensureQuota(uid);
        if (quota.unlimited()) {
            return unlimited();
        }
        if (friendOfOwner(uid)) {
            // Безлимит выдаётся один раз и навсегда: иначе дружбу пришлось бы
            // перепроверять при каждом заходе на главную.
            store.grantUnlimited(uid);
            return unlimited();
        }
        int remaining = quota.remaining();
        return new Entitlement(remaining > 0, false, remaining);
    }

    @Override
    @Transactional
    public boolean consume(String uid, String roomId, int gameNumber) {
        if (entitlement(uid, accountEmail(uid)).unlimited()) {
            return false;
        }
        SabotageStore.Spending spending = store.spendFreeGame(uid, roomId, gameNumber);
        if (spending == SabotageStore.Spending.EXHAUSTED) {
            // Журнальная запись, сделанная шагом раньше, откатывается вместе
            // с этой транзакцией: «списание есть, а счётчик не вырос» не
            // остаётся.
            throw ApiException.of("SABOTAGE_LIMIT_REACHED", 402);
        }
        return spending == SabotageStore.Spending.SPENT;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> loadout(String uid) {
        return store.loadout(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> requireLoadout(String uid) {
        List<String> loadout = store.loadout(uid);
        if (loadout.size() != LOADOUT_SIZE) {
            throw ApiException.of("DEFAULT_LOADOUT_REQUIRED", 409);
        }
        return loadout;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> loadouts(Collection<String> uids) {
        return store.loadouts(uids);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> requireLoadouts(Collection<String> uids) {
        Map<String, List<String>> loadouts = store.loadouts(uids);
        for (String uid : uids) {
            if (loadouts.getOrDefault(uid, List.of()).size() != LOADOUT_SIZE) {
                throw ApiException.of("DEFAULT_LOADOUT_REQUIRED", 409);
            }
        }
        return loadouts;
    }

    /**
     * Записать обойму учётки.
     *
     * <p>Существование роликов проверяется здесь тем же вопросом, каким его
     * задаёт партия ({@code SetMatchLoadoutUseCase}, {@code
     * ReplaceLoadoutSlotUseCase}) — одним чтением на весь список. До этого
     * проверки не было вовсе: адрес принимал любые пять строк, отвечал
     * {@code ready: true}, и обойма из несуществующих мемов проходила все
     * заставы, где спрашивают {@link #requireLoadout} — создание комнаты, вход
     * в неё, место зрителя, приглашение, билет подбора, преполёт пары. Там
     * проверяется только РАЗМЕР обоймы, потому что содержимое считалось
     * проверенным при записи.
     *
     * <p>Спрашивается каталог партии, а не библиотека медиа напрямую: у первого
     * ответ шире на встроенные ролики, и обойма, принятая здесь, обязана быть
     * принята партией. Возьми мы более узкий вопрос — игрок сохранил бы обойму,
     * которую партия потом отвергнет, и наоборот.
     *
     * <p>Черновик короче пяти по-прежнему разрешён: неполнота — не ошибка
     * (см. {@code SaveDefaultLoadoutRequestDTO}), а вот несуществующий ролик —
     * ошибка при любой длине.
     */
    @Override
    @Transactional
    public List<String> saveLoadout(String uid, List<String> memeIds) {
        List<String> wanted = LoadoutRules.normalize(memeIds);
        if (!wanted.isEmpty() && !memes.existing(wanted).containsAll(wanted)) {
            throw ApiException.of("MEME_NOT_FOUND", 404);
        }
        return store.replaceLoadout(uid, memeIds);
    }

    /**
     * Чистая проверка: в базу не ходит вовсе. Спрашивающий уже держит список —
     * это его копия обоймы, — и ответ на «полна ли» стоит не чтения, а
     * приведения к каноническому виду тем же правилом, что при сохранении.
     */
    @Override
    public boolean loadoutCharged(List<String> memeIds) {
        return LoadoutRules.charged(memeIds);
    }

    /** Тоже чистый ответ: набор выводится из каталога оружия, а не из базы. */
    @Override
    public Map<String, Integer> baseArsenal() {
        return WeaponRegistry.baseArsenal();
    }

    /**
     * Дружба с владельцем сервиса — в обоих хранилищах.
     *
     * <p>Связи, заведённые до переезда, лежат в {@code friend_link}, новые — в
     * области дружбы. Спросить одно значило бы отобрать безлимит у половины
     * друзей владельца.
     */
    private boolean friendOfOwner(String uid) {
        String ownerUid = accounts.ownerUid().orElse(null);
        if (ownerUid == null || ownerUid.equals(uid)) {
            return false;
        }
        return getterSocial.getLink(Ids.pair(ownerUid, uid)).isPresent()
                || friendships.areFriends(ownerUid, uid);
    }

    /** Почта нужна списанию затем же, зачем и чтению: узнать владельца сервиса. */
    private String accountEmail(String uid) {
        return accounts.account(uid).map(AccountPort.Account::email).orElse(null);
    }

    private static Entitlement unlimited() {
        return new Entitlement(true, true, null);
    }
}
