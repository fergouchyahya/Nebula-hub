package nebula.core;

import java.util.concurrent.atomic.LongAdder;

public final class Metrics {
    private final LongAdder ops = new LongAdder();

    /** Incrémente de 1. */
    public void mark() {
        ops.increment();
    }

    /** Incrémente de n (n>0). */
    public void mark(long n) {
        if (n <= 0)
            throw new IllegalArgumentException("n>0 required");
        ops.add(n);
    }

    /** Lecture sans reset. */
    public long snapshot() {
        return ops.sum();
    }

    /** Lecture avec reset atomique (utile pour des taux/s intervalles). */
    public long snapshotAndReset() {
        return ops.sumThenReset();
    }
}
