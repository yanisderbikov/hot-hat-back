package ru.hothat.profile.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Журнал согласий в таблице {@code v2.legal_consent}.
 *
 * <p>Строка на документ, а не одно поле {@code jsonb}: «принял ли он нынешнюю
 * редакцию правил» — вопрос про конкретный документ конкретной версии, и по
 * json-полю он не отвечался без разбора карты в памяти.
 */
@Repository
interface PlayerConsents extends JpaRepository<PlayerConsent, Long> {

    List<PlayerConsent> findByPlayerIdOrderByAcceptedAtDesc(UUID playerId);

    boolean existsByPlayerIdAndDocumentAndDocumentVersion(UUID playerId, String document, String version);
}
