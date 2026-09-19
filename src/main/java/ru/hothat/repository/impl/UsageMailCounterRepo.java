package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.ops.UsageMailCounter;

@Repository
interface UsageMailCounterRepo extends JpaRepository<UsageMailCounter, String> {
}
