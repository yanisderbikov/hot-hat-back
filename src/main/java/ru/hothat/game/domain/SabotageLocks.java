package ru.hothat.game.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Занятость дорожек эффектов на сцене.
 *
 * <p>Каждое поле — момент времени, до которого дорожка занята. Так, а не
 * флагом «занято», потому что снимать блокировку некому: игрок, поставивший
 * эффект, может выйти, и висящий флаг остался бы навсегда.
 *
 * <p>Запись съёмки Подмены заблокирована отдельно и поимённо: десять игроков
 * вправе снимать разных объясняющих одновременно, поэтому общий срок здесь не
 * годится.
 */
public final class SabotageLocks {

    private long videoUntil;
    private long voiceUntil;
    private long crocodileUntil;
    private long overlayUntil;
    private long replacementUntil;
    private String replacementTurnId = "";
    private final Map<String, Long> replacementRecordingByAttacker = new LinkedHashMap<>();

    public long videoUntil() {
        return videoUntil;
    }

    public long voiceUntil() {
        return voiceUntil;
    }

    public long crocodileUntil() {
        return crocodileUntil;
    }

    public long overlayUntil() {
        return overlayUntil;
    }

    public long replacementUntil() {
        return replacementUntil;
    }

    public String replacementTurnId() {
        return replacementTurnId;
    }

    public Map<String, Long> replacementRecordingByAttacker() {
        return replacementRecordingByAttacker;
    }

    public void setVideoUntil(long value) {
        this.videoUntil = Math.max(0, value);
    }

    public void setVoiceUntil(long value) {
        this.voiceUntil = Math.max(0, value);
    }

    public void setCrocodileUntil(long value) {
        this.crocodileUntil = Math.max(0, value);
    }

    public void setOverlayUntil(long value) {
        this.overlayUntil = Math.max(0, value);
    }

    public void setReplacementUntil(long value) {
        this.replacementUntil = Math.max(0, value);
    }

    public void setReplacementTurnId(String value) {
        this.replacementTurnId = value == null ? "" : value;
    }

    /** Срок занятости названной дорожки; для {@link EffectLock#NONE} — ноль. */
    public long until(EffectLock lock) {
        return switch (lock) {
            case VIDEO -> videoUntil;
            case VOICE -> voiceUntil;
            case CROCODILE -> crocodileUntil;
            case OVERLAY -> overlayUntil;
            case REPLACEMENT -> replacementUntil;
            case NONE -> 0;
        };
    }

    public void occupy(EffectLock lock, long until) {
        switch (lock) {
            case VIDEO -> setVideoUntil(until);
            case VOICE -> setVoiceUntil(until);
            case CROCODILE -> setCrocodileUntil(until);
            case OVERLAY -> setOverlayUntil(until);
            case REPLACEMENT -> setReplacementUntil(until);
            case NONE -> {
                // Помидор, мем и пук ничего на сцене не занимают.
            }
        }
    }

    public boolean busy(EffectLock lock, long nowMs) {
        return until(lock) > nowMs;
    }

    /** Идёт ли Подмена: во время неё разрешены только помидоры, мемы и пук. */
    public boolean replacementRunning(long nowMs) {
        return replacementUntil > nowMs;
    }

    public boolean recordingBusy(String attackerUid, long nowMs) {
        Long until = replacementRecordingByAttacker.get(attackerUid);
        return until != null && until > nowMs;
    }

    public void startRecording(String attackerUid, long untilMs) {
        replacementRecordingByAttacker.put(attackerUid, Math.max(0, untilMs));
    }

    public void stopRecording(String attackerUid) {
        replacementRecordingByAttacker.remove(attackerUid);
    }

    /** Партия началась заново — старые сроки к ней отношения не имеют. */
    public void clear() {
        videoUntil = 0;
        voiceUntil = 0;
        crocodileUntil = 0;
        overlayUntil = 0;
        replacementUntil = 0;
        replacementTurnId = "";
        replacementRecordingByAttacker.clear();
    }
}
