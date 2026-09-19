package ru.hothat.game.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Слово в шляпе и его место в колоде.
 *
 * <p>Заменяет jsonb {@code bag} — массив строк в строке комнаты, который
 * переписывался целиком на каждое вытянутое слово вместе со всеми ста тремя
 * колонками. Здесь вытянуть слово — это обновление одной строки по индексу.
 *
 * <p>{@link #bagPosition} задаёт порядок. Пропущенное слово возвращается в
 * шляпу с позицией {@code max+1} под той же блокировкой хода (§6.4), поэтому
 * оно не выпадет следующим же нажатием — сегодня перетасовка всего массива
 * этого не гарантировала.
 *
 * <p>{@code @Version} нет намеренно: строку слова трогает только сценарий
 * хода, уже держащий блокировку {@link MatchTurn}.
 */
@Entity
@Table(name = "match_word", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MatchWord {

    /**
     * in_bag | drawn | guessed — набор закрыт ограничением базы. Пропущенное
     * слово — это снова {@code in_bag} с новой позицией, отдельного состояния
     * ему не нужно; отменённое апелляцией — тоже.
     */
    static final String IN_BAG = "in_bag";
    static final String DRAWN = "drawn";
    static final String GUESSED = "guessed";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(nullable = false, updatable = false, length = 80)
    private String word;

    @Column(name = "bag_position", nullable = false)
    private Integer bagPosition;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String state = IN_BAG;

    /** Кто сдал слово: по нему слова возвращаются в шляпу при роспуске партии. */
    @Column(name = "submitted_by", updatable = false)
    private UUID submittedBy;
}
