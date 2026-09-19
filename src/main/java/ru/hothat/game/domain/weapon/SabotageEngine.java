package ru.hothat.game.domain.weapon;

import ru.hothat.config.ApiException;
import ru.hothat.game.domain.EffectLock;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.MemeQueue;
import ru.hothat.game.domain.RandomSource;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.domain.TurnRules;
import ru.hothat.game.domain.Weapon;
import ru.hothat.game.domain.WeaponRegistry;
import ru.hothat.game.domain.WeaponType;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Диверсии: общие правила стрельбы и применение решения обработчика.
 *
 * <p>Раньше это была одна процедура на 220 строк с тринадцатью ветками по
 * строке {@code type} ({@code GameServiceImpl.useSabotage}), и добавление
 * оружия требовало правки в тринадцати с лишним местах (замечание F2 аудита).
 * Здесь общие проверки написаны один раз и читают каталог, а особенное знает
 * только обработчик своего вида.
 */
public final class SabotageEngine {

    private final Clock clock;
    private final RandomSource random;

    public SabotageEngine(Clock clock, RandomSource random) {
        this.clock = clock;
        this.random = random;
    }

    /**
     * Выстрел: проверка общих правил, решение обработчика, списание.
     *
     * @param state    партия; меняется на месте — событие, дорожки, история
     * @param attacker стрелок; меняется на месте — боезапас, обойма, перезарядка
     */
    public SabotageOutcome fire(MatchState state, MatchPlayer attacker, SabotageCommand command) {
        long now = clock.millis();
        Weapon weapon = WeaponRegistry.of(command.type());
        requireShootable(state, attacker, weapon, command.owner(), now);

        SabotageDecision decision = WeaponHandlers.of(command.type())
                .fire(new SabotageContext(state, attacker, command, now, random));
        SabotageEvent event = decision.event();

        // У владельца негатив и апож не кончаются, но пока заряды есть — тратятся:
        // иначе его панель показывала бы неизрасходованный запас всю партию.
        boolean unlimited = weapon.ownerUnlimited() && command.owner();
        if (weapon.consumesAmmo() && (!unlimited || attacker.ammo(weapon.ammoKey()) > 0)) {
            attacker.spend(weapon.ammoKey(), 1);
        }
        if (decision.consumeMeme()) {
            burnMeme(attacker, event.memeId());
        }
        if (!weapon.cooldownExempt()) {
            attacker.setSabotageCooldownUntil(now + WeaponRegistry.COOLDOWN_MS);
        }
        if (weapon.lock() != EffectLock.NONE) {
            state.getLocks().occupy(weapon.lock(), decision.lockUntilMs());
        }
        if (weapon.lock() == EffectLock.REPLACEMENT) {
            // Ход помечен: вторая Подмена в том же ходу невозможна.
            state.getLocks().setReplacementTurnId(state.getTurnId() == null ? "" : state.getTurnId());
        }
        if (decision.consumedClipId() != null) {
            state.getClips().remove(decision.consumedClipId());
        }
        remember(state, event);
        return new SabotageOutcome(event, Map.copyOf(attacker.getArsenal()), attacker.getSabotageCooldownUntil());
    }

    /**
     * Заказ съёмки Подмены: атакующий снимает объясняющего десять секунд.
     *
     * <p>Проверки здесь свои, более узкие, чем при выстреле. Ни заряд, ни
     * перезарядка, ни отметка «Подмена в этом ходу уже была» съёмке не мешают:
     * слот — это плёнка, а не патрон, тратится он при применении клипа. Иначе
     * брошенная съёмка съедала бы единственный заряд, а снять запас на будущие
     * ходы стало бы невозможно.
     */
    public SabotageEvent orderClip(MatchState state, MatchPlayer attacker, String targetUid) {
        long now = clock.millis();
        Weapon weapon = WeaponRegistry.of(WeaponType.REPLACEMENT);
        if (state.getPhase() != MatchPhase.ACTIVE) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        if (state.isPaused()) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
        if (!state.sabotageAllowed()) {
            throw ApiException.of("WEAPON_INVALID", 409);
        }
        if (state.getExplainerUid() == null || state.getExplainerUid().isBlank()) {
            throw ApiException.of("EXPLAINER_MISSING", 409);
        }
        if (!state.isPlayer(attacker.getUid())) {
            throw ApiException.of("WEAPON_NOT_ELIGIBLE", 403);
        }
        if (state.activeRoster().contains(attacker.getUid())) {
            throw ApiException.of("ACTIVE_TEAM_CANNOT_ATTACK", 403);
        }
        if (!TurnRules.fitsInTurn(state, now, weapon.minimumTurnRemainingMs())) {
            throw ApiException.of("NOT_ENOUGH_TURN_TIME", 409);
        }
        if (targetUid != null && !targetUid.equals(state.getExplainerUid())) {
            // Кадр уже сменился: снимать надо того, кто объясняет сейчас.
            throw ApiException.of("REPLACEMENT_WRONG_TARGET", 409);
        }
        ReplacementClipRules.dropAbandoned(state, now);
        if (ReplacementClipRules.ownedBy(state, attacker.getUid()) >= ReplacementClipRules.SLOTS) {
            throw ApiException.of("REPLACEMENT_RECORD_LIMIT", 409);
        }
        if (state.getLocks().recordingBusy(attacker.getUid(), now)) {
            throw ApiException.of("REPLACEMENT_RECORD_BUSY", 409);
        }
        return ReplacementClipRules.order(state, attacker, now, random);
    }

    /** Общие правила стрельбы — те, что не зависят от вида оружия. */
    private void requireShootable(MatchState state, MatchPlayer attacker, Weapon weapon,
                                  boolean owner, long now) {
        if (state.getPhase() != MatchPhase.ACTIVE) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        if (state.isPaused()) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
        if (state.getExplainerUid() == null || state.getExplainerUid().isBlank()) {
            throw ApiException.of("EXPLAINER_MISSING", 409);
        }
        if (weapon.advanced() && !state.sabotageAllowed()) {
            // Расширенный арсенал живёт только в режиме диверсий и не в тестовой комнате.
            throw ApiException.of("WEAPON_INVALID", 409);
        }
        if (weapon.ownerOnly() && !owner) {
            throw ApiException.of("OWNER_ONLY", 403);
        }
        if (!state.isPlayer(attacker.getUid())) {
            throw ApiException.of("WEAPON_NOT_ELIGIBLE", 403);
        }
        if (state.activeRoster().contains(attacker.getUid())) {
            throw ApiException.of("ACTIVE_TEAM_CANNOT_ATTACK", 403);
        }
        if (!weapon.cooldownExempt() && attacker.getSabotageCooldownUntil() > now) {
            throw ApiException.of("WEAPON_COOLDOWN", 429);
        }
        if (state.getLocks().replacementRunning(now) && !weapon.allowedDuringReplacement()) {
            // Во время Подмены на сцене чужое лицо: другие эффекты сломали бы кадр.
            throw ApiException.of("REPLACEMENT_ACTIVE", 409);
        }
        requireFreeLock(state, weapon, now);
        if (!TurnRules.fitsInTurn(state, now, weapon.minimumTurnRemainingMs())) {
            throw ApiException.of("NOT_ENOUGH_TURN_TIME", 409);
        }
        boolean unlimited = weapon.ownerUnlimited() && owner;
        if (weapon.consumesAmmo() && !unlimited && attacker.ammo(weapon.ammoKey()) <= 0) {
            throw ApiException.of("NO_AMMO", 409);
        }
    }

    /** Дорожка эффекта занята: две видеодиверсии одновременно не показать. */
    private void requireFreeLock(MatchState state, Weapon weapon, long now) {
        switch (weapon.lock()) {
            case VIDEO -> {
                if (state.getLocks().busy(EffectLock.VIDEO, now)) {
                    throw ApiException.of("VIDEO_EFFECT_BUSY", 409);
                }
            }
            case VOICE -> {
                if (state.getLocks().busy(EffectLock.VOICE, now)) {
                    throw ApiException.of("VOICE_EFFECT_BUSY", 409);
                }
            }
            case OVERLAY -> {
                if (state.getLocks().busy(EffectLock.OVERLAY, now)) {
                    throw ApiException.of("OVERLAY_EFFECT_BUSY", 409);
                }
            }
            case REPLACEMENT -> {
                String turnId = state.getTurnId() == null ? "" : state.getTurnId();
                if (!state.getLocks().replacementTurnId().isEmpty()
                        && state.getLocks().replacementTurnId().equals(turnId)) {
                    throw ApiException.of("REPLACEMENT_ALREADY_USED", 409);
                }
                if (state.getLocks().busy(EffectLock.VIDEO, now) || state.getLocks().busy(EffectLock.VOICE, now)
                        || state.getLocks().busy(EffectLock.CROCODILE, now)) {
                    throw ApiException.of("REPLACEMENT_CONFLICT", 409);
                }
            }
            case CROCODILE, NONE -> {
                // Крокодилов может быть несколько подряд, а помидоры и мемы сцену не занимают.
            }
        }
    }

    private void burnMeme(MatchPlayer attacker, String memeId) {
        MemeQueue queue = LoadoutRules.queue(attacker);
        queue.burn(memeId);
        LoadoutRules.apply(attacker, queue);
        List<String> used = new ArrayList<>(attacker.getUsedMemeIds());
        if (!used.contains(memeId)) {
            used.add(memeId);
        }
        attacker.setUsedMemeIds(used);
    }

    /**
     * История последних диверсий партии. Событие прошлой партии из неё
     * выбрасывается: сцена не должна доигрывать то, что было до перезапуска.
     */
    private void remember(MatchState state, SabotageEvent event) {
        List<SabotageEvent> recent = new ArrayList<>(state.getRecentEvents());
        recent.add(event);
        List<SabotageEvent> current = new ArrayList<>();
        for (SabotageEvent item : recent) {
            if (item.gameNumber() == state.getGameNumber()) {
                current.add(item);
            }
        }
        if (current.size() > SabotageEvent.RECENT_LIMIT) {
            current = new ArrayList<>(current.subList(current.size() - SabotageEvent.RECENT_LIMIT, current.size()));
        }
        state.setRecentEvents(current);
        state.setLastEvent(event);
    }
}
