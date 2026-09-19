package ru.hothat.admin.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * След решений модератора в таблице {@code v2.moderation_action}.
 *
 * <p>Появилась потому, что сохранять автора решения было некуда: снятие бана
 * писало имя администратора в журнал приложения — единственное место, где оно
 * оставалось, и первая же ротация логов его уносила.
 */
@Repository
interface ModerationActions extends JpaRepository<ModerationAction, Long> {
}
