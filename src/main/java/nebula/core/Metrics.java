package nebula.core;

import java.util.concurrent.atomic.AtomicLong;

public class Metrics {
    private final AtomicLong ops = new AtomicLong();

    public void mark() {
        ops.incrementAndGet();
    }

    public long snapshot() {
        return ops.get();
    }

}
