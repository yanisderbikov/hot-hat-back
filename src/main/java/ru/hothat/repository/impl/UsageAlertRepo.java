package ru.hothat.repository.impl;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.ops.UsageAlert;

import java.util.List;

@Repository
interface UsageAlertRepo extends JpaRepository<UsageAlert, String> {

    List<UsageAlert> findAllByOrderByCreatedAtDesc(Limit limit);
}
