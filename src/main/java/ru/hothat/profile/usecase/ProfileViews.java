package ru.hothat.profile.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.profile.api.dto.ConsentVersionsView;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.profile.api.dto.MemeLoadoutStatusView;
import ru.hothat.profile.api.dto.MyConsentsResponseDTO;
import ru.hothat.profile.api.dto.ProfileCardView;
import ru.hothat.profile.api.dto.RecordedConsentResponseDTO;
import ru.hothat.profile.api.dto.SabotageEntitlementView;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Divisions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Проекции области профиля: запись хранилища → ответ контракта.
 *
 * <p>Собраны в одном классе не ради экономии файлов, а потому что одна и та же
 * карточка едет из пяти адресов (своя карточка, онбординг, смена языка,
 * согласия, чужая карточка), и разойтись пяти ответам о том же человеке
 * нельзя. Проекция при этом собирается <b>поле за полем</b>: ни почта, ни
 * поколение токенов сюда не попадают физически — их нет в записи хранилища.
 */
@Component
public class ProfileViews {

    /** Порядок документов совпадает с контрактом; менять его нельзя — это форма ответа. */
    private static final List<String> CONSENT_KEYS =
            List.of("agreement", "privacy", "personalData", "community", "recording", "divisions");

    /**
     * Карточка своего профиля.
     *
     * <p>Признак «есть команда» приходит аргументом, а не берётся из карточки:
     * у принадлежности к команде один хозяин — строка состава в области
     * команды. От признака зависит значок дивизиона: без команды это название
     * языка, с ней — название дивизиона.
     */
    public ProfileCardView profileCard(ProfileStore.Card card, boolean hasRankedTeam) {
        String divisionLanguage = Divisions.normalize(card.divisionLanguage());
        return new ProfileCardView(
                card.nickname(),
                emptyToNull(card.avatarDataUrl()),
                DivisionLanguage.fromWire(divisionLanguage),
                DivisionLanguage.fromWire(
                        Divisions.allowedUiLanguage(divisionLanguage, card.uiLanguage())),
                Divisions.badge(divisionLanguage, hasRankedTeam),
                hasRankedTeam);
    }

    /**
     * Счётчик обоймы мемов. Размер называется один раз, из
     * {@link SabotageArmoryPort#LOADOUT_SIZE} — у владельца обоймы, — а не
     * четвёртой копией числа «пять».
     */
    public MemeLoadoutStatusView memeLoadout(List<String> loadout) {
        int selected = loadout == null ? 0 : loadout.size();
        return new MemeLoadoutStatusView(selected, SabotageArmoryPort.LOADOUT_SIZE,
                selected == SabotageArmoryPort.LOADOUT_SIZE);
    }

    /**
     * Квота диверсий. Приезжает записью порта своей области, а не картой
     * {@code Map<String, Object>}: пока квоту считал движок профиля, поля
     * ответа собирались по строковым ключам, и опечатка в ключе была
     * невидима до прода. {@code remaining} у безлимита остаётся {@code null}:
     * ноль означал бы «партии кончились», то есть обратное.
     */
    public SabotageEntitlementView sabotageEntitlement(SabotageArmoryPort.Entitlement entitlement) {
        return new SabotageEntitlementView(
                entitlement.allowed(), entitlement.unlimited(), entitlement.remaining());
    }

    public MyConsentsResponseDTO consents(ProfileStore.Consents consents) {
        return new MyConsentsResponseDTO(
                consents.accepted(),
                consents.adultConfirmed(),
                consents.acceptedAtMs(),
                versions(consents.versions()));
    }

    public RecordedConsentResponseDTO recordedConsent(ProfileStore.Consents consents, long acceptedAtMs) {
        return new RecordedConsentResponseDTO(
                consents.acceptedAtMs() == null ? acceptedAtMs : consents.acceptedAtMs(),
                consents.adultConfirmed(),
                versions(consents.versions()));
    }

    /** Обратный перевод: тело запроса → карта «документ → версия» для хранилища. */
    public Map<String, String> versionsOf(ConsentVersionsView versions) {
        Map<String, String> stored = new LinkedHashMap<>();
        if (versions == null) {
            return stored;
        }
        stored.put("agreement", versions.agreement());
        stored.put("privacy", versions.privacy());
        stored.put("personalData", versions.personalData());
        stored.put("community", versions.community());
        stored.put("recording", versions.recording());
        stored.put("divisions", versions.divisions());
        return stored;
    }

    /**
     * Согласий нет вовсе — вместо шестёрки пустых строк едет {@code null}:
     * «не принимал» и «принял пустое» — разные состояния, и экран их различает.
     */
    private static ConsentVersionsView versions(Map<String, String> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        boolean anything = false;
        for (String key : CONSENT_KEYS) {
            if (emptyToNull(versions.get(key)) != null) {
                anything = true;
                break;
            }
        }
        if (!anything) {
            return null;
        }
        return new ConsentVersionsView(
                emptyToNull(versions.get("agreement")),
                emptyToNull(versions.get("privacy")),
                emptyToNull(versions.get("personalData")),
                emptyToNull(versions.get("community")),
                emptyToNull(versions.get("recording")),
                emptyToNull(versions.get("divisions")));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
