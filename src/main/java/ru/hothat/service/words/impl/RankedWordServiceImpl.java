package ru.hothat.service.words.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.hothat.game.store.RankedWordGenerator;
import ru.hothat.service.words.RankedWordService;

import java.util.List;

/**
 * Прежний фасад генератора слов: теперь переходник к {@link RankedWordGenerator}.
 *
 * <p>Словари и отбор переехали к владельцу партии. Фасад жив, пока живёт
 * старый движок игры, который им пользуется.
 */
@Service
@RequiredArgsConstructor
public class RankedWordServiceImpl implements RankedWordService {

    private final RankedWordGenerator generator;

    @Override
    public String normalize(String value, String language) {
        return generator.normalize(value, language);
    }

    @Override
    public List<String> generate(int count, List<List<String>> playerHistories, String language) {
        return generator.generate(count, playerHistories, language);
    }
}
