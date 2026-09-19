package ru.hothat.repository.impl;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.ops.UsageDaily;

import java.util.List;

@Repository
interface UsageDailyRepo extends JpaRepository<UsageDaily, String> {

    List<UsageDaily> findAllByOrderByDateDesc(Limit limit);

    List<UsageDaily> findByDateBetweenOrderByDateDesc(String start, String end, Limit limit);
}
