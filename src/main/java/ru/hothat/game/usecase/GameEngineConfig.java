package ru.hothat.game.usecase;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.RandomSource;
import ru.hothat.game.domain.weapon.SabotageEngine;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * Сборка движка: часы и жребий подаются снаружи.
 *
 * <p>Домен не знает Spring, поэтому собирает его этот класс — единственное
 * место, где у движка появляются настоящие часы и настоящая случайность. В
 * тесте на их место подставляются свои, и партия становится воспроизводимой:
 * ровно этого не хватало 124 прямым обращениям к
 * {@code System.currentTimeMillis()}, найденным аудитом (F10).
 */
@Configuration
public class GameEngineConfig {

    /**
     * Часы приложения. Условный бин: часы нужны не только партии, и объявить
     * их могла соседняя область — тогда общими будут её.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Жребий партии.
     *
     * <p>Криптостойкий, как и прежний {@code Shuffle}: слово из шляпы
     * предсказывать не должен никто, включая того, кто её собирал.
     */
    @Bean
    public RandomSource randomSource() {
        SecureRandom random = new SecureRandom();
        return bound -> bound <= 0 ? 0 : random.nextInt(bound);
    }

    @Bean
    public MatchEngine matchEngine(Clock clock, RandomSource randomSource) {
        return new MatchEngine(clock, randomSource);
    }

    @Bean
    public SabotageEngine sabotageEngine(Clock clock, RandomSource randomSource) {
        return new SabotageEngine(clock, randomSource);
    }
}
