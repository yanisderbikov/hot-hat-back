package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.HotHatProperties;

/**
 * Постоянный адрес файла мема.
 *
 * <p>Подпись хранилища живёт час, поэтому в карточке мема лежит не она, а
 * ссылка на наше поддерево: за ним стоит редирект, который подписывает
 * ссылку заново на каждый запрос. Адрес собирается в одном месте — иначе
 * его форма разъехалась бы между выдачей билета и публикацией карточки,
 * а он записывается в базу и переживает обе операции.
 */
@Component
@RequiredArgsConstructor
public class MemeFileUrls {

    /** Поддерево {@code MemeFileController}; сегмент {@code memes/} — часть ключа. */
    public static final String FILES_PREFIX = "/api/v2/media/files/";

    private final HotHatProperties properties;

    public String stable(String storageKey) {
        return properties.apiOrigin() + FILES_PREFIX + storageKey;
    }
}
