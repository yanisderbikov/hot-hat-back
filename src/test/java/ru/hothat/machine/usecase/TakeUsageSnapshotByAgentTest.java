package ru.hothat.machine.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.admin.spi.UsageSnapshotPort;
import ru.hothat.machine.api.dto.MachineUsageSnapshotResponseDTO;
import ru.hothat.machine.api.dto.UsageSnapshotState;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Приход агента наблюдения: снимать показания или нет.
 *
 * <p>Окно планового снятия считает область наблюдения, и здесь проверяется
 * именно это разделение: машина не решает, четыре ли раза в сутки снимать, —
 * она спрашивает владельца снимков и, получив отказ, НЕ снимает ничего.
 * Раньше отказ и снимок различались только полем в карте, и пропущенный
 * прогон было легко принять за состоявшийся.
 */
class TakeUsageSnapshotByAgentTest {

    /** Наблюдатель, который отвечает то, что ему велели, и считает вызовы. */
    private static final class FakeUsageSnapshots implements UsageSnapshotPort {

        private final boolean windowOpen;
        private int taken;

        private FakeUsageSnapshots(boolean windowOpen) {
            this.windowOpen = windowOpen;
        }

        @Override
        public boolean scheduledWindowAllowed() {
            return windowOpen;
        }

        @Override
        public Taken takeScheduled() {
            taken++;
            return new Taken(Instant.parse("2026-09-06T05:00:03Z"), 2, true);
        }
    }

    @Test
    @DisplayName("В плановом окне снимок снимается и отвечает временем, тревогами и отчётом")
    void insideTheWindowTheSnapshotIsTaken() {
        FakeUsageSnapshots usage = new FakeUsageSnapshots(true);

        MachineUsageSnapshotResponseDTO answer = new TakeUsageSnapshotByAgentUseCase(usage).run();

        assertThat(answer.state()).isEqualTo(UsageSnapshotState.TAKEN);
        assertThat(answer.collectedAt()).isEqualTo("2026-09-06T05:00:03Z");
        assertThat(answer.thresholdAlertCount()).isEqualTo(2);
        assertThat(answer.dailyReportSent()).isTrue();
        assertThat(usage.taken).isEqualTo(1);
    }

    @Test
    @DisplayName("Вне окна снимок не снимается вовсе, а не снимается и выбрасывается")
    void outsideTheWindowNothingIsTaken() {
        FakeUsageSnapshots usage = new FakeUsageSnapshots(false);

        MachineUsageSnapshotResponseDTO answer = new TakeUsageSnapshotByAgentUseCase(usage).run();

        assertThat(answer.state()).isEqualTo(UsageSnapshotState.SKIPPED_OUTSIDE_WINDOW);
        assertThat(answer.collectedAt()).isNull();
        assertThat(answer.thresholdAlertCount()).isZero();
        assertThat(answer.dailyReportSent()).isFalse();
        assertThat(usage.taken).isZero();
    }
}
