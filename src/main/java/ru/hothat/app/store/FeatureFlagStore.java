package ru.hothat.app.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единственная дверь области {@code app} в таблицу переключателей.
 *
 * <p>Кеш на полминуты остался от переходного движка и по той же причине:
 * флаг спрашивают на каждой загрузке страницы и на каждом запросе к тест-ботам,
 * а меняют его руками раз в месяц. Полминуты — компромисс между лишними
 * чтениями и задержкой, с которой переключение доходит до игроков.
 *
 * <p>Неизвестное имя — «выключено», а не ошибка: выключенная возможность
 * безопаснее случайно включённой из-за опечатки в названии. Отвергнуть
 * незнакомое имя успевает проверка на входе ({@code FeatureFlagName}), и
 * до кеша оно не доходит вовсе — иначе каждое выдуманное имя заводило бы
 * здесь вечную запись.
 */
@Component
@RequiredArgsConstructor
public class FeatureFlagStore {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private record Cached(boolean enabled, Instant readAt) {
    }

    private final FeatureToggles toggles;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** Включена ли возможность; неизвестное имя — {@code false}. */
    @Transactional(readOnly = true)
    public boolean enabled(String name) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        Cached cached = cache.get(key);
        if (cached != null && cached.readAt().plus(CACHE_TTL).isAfter(now)) {
            return cached.enabled();
        }
        boolean enabled = toggles.findById(key)
                .map(flag -> Boolean.TRUE.equals(flag.getEnabled()))
                .orElse(false);
        cache.put(key, new Cached(enabled, now));
        return enabled;
    }
}
