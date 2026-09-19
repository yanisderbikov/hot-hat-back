package ru.hothat.admin.domain;

/**
 * Машинные имена метрик снимка.
 *
 * <p>Имя — путь метрики в прежнем снимке ({@code database.storage},
 * {@code vps.networkMonthly}), и это не случайность: по нему история сшивается
 * с тем, что уже лежит в базе, а карточка админки рисует ту же строку таблицы.
 *
 * <p>Ключ существует ровно потому, что подписи меняются. Подпись метрики базы
 * уже менялась вместе с самой базой («Firestore · чтения» → «PostgreSQL ·
 * размер базы»), а тревога о превышении порога опознавалась ИМЕННО подписью,
 * приведённой к нижнему регистру, — переименование заводило вторую тревогу о
 * том же и слало второе письмо. Здесь опознаётся ключ, а подпись хранится
 * рядом как исторический текст.
 */
public final class MetricKey {

    public static final String DATABASE_READS = "database.reads";
    public static final String DATABASE_WRITES = "database.writes";
    public static final String DATABASE_DELETES = "database.deletes";
    public static final String DATABASE_STORAGE = "database.storage";
    public static final String HOSTING_TRANSFER = "hosting.transfer";
    public static final String HOSTING_STORAGE = "hosting.storage";
    public static final String AUTH_ACTIVE_USERS = "auth";
    public static final String VPS_DISK = "vps.disk";
    public static final String VPS_MEMORY = "vps.memory";
    public static final String VPS_MEDIA_STORAGE = "vps.mediaStorage";
    public static final String VPS_NETWORK_TOTAL = "vps.networkTotal";
    public static final String VPS_NETWORK_MONTHLY = "vps.networkMonthly";
    public static final String MAIL_DAILY = "mail.daily";
    public static final String MAIL_MONTHLY = "mail.monthly";

    private MetricKey() {
    }
}
