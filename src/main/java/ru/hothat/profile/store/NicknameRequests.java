package ru.hothat.profile.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Заявки на занятый ник в таблице {@code v2.nickname_change_request}.
 *
 * <p>Заменяет {@code support_request} с её мёртвыми колонками {@code type} и
 * {@code destination}: вид заявки был один, а адрес получателя — это
 * настройка сервиса, а не свойство заявки.
 */
@Repository
interface NicknameRequests extends JpaRepository<NicknameRequest, Long> {

    /** Открытая заявка у игрока может быть только одна: это держит ux-индекс. */
    boolean existsByPlayerIdAndStatus(UUID playerId, String status);
}
