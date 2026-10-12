package backend.gateway;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class CircuitBreaker {

    private static final int FAILURE_THRESHOLD = 5;
    private static final long OPEN_FOR_MS = 15_000;

    private final AtomicInteger failures = new AtomicInteger();
    private volatile long openedAt;

    public boolean isOpen() {
        if (openedAt == 0) {
            return false;
        }
        if (System.currentTimeMillis() - openedAt > OPEN_FOR_MS) {
            openedAt = 0;
            failures.set(0);
            return false;
        }
        return true;
    }

    public void recordSuccess() {
        failures.set(0);
        openedAt = 0;
    }

    public void recordFailure() {
        if (failures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openedAt = System.currentTimeMillis();
        }
    }
}
