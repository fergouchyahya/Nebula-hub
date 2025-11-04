package nebula.rate;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ParkingSemaphore {
    private final Semaphore sem;
    private final int capacity;

    public ParkingSemaphore(int slots) {
        if (slots <= 0)
            throw new IllegalArgumentException("slots > 0 required");
        this.capacity = slots;
        this.sem = new Semaphore(slots, true);
    }

    public void enter() throws InterruptedException {
        sem.acquire();
    }

    public boolean tryEnter() {
        return sem.tryAcquire();
    }

    public boolean tryEnter(long t, TimeUnit u) throws InterruptedException {
        Objects.requireNonNull(u, "unit");
        return sem.tryAcquire(t, u);
    }

    public void leave() {
        sem.release();
    }

    public int capacity() {
        return capacity;
    }

    public int approxAvailable() {
        return sem.availablePermits();
    }

    public int queued() {
        return sem.getQueueLength();
    }
}
