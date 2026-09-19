package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.ops.UsageMonthly;

@Repository
interface UsageMonthlyRepo extends JpaRepository<UsageMonthly, String> {
}
