package nebula.rate;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Limiteur de concurrence (max N tâches simultanées). */
public class Parking {
    private final Semaphore sem;
    private final int capacity;

    public Parking(int slots) {
        if (slots <= 0)
            throw new IllegalArgumentException("slots > 0 required");
        this.capacity = slots;
        this.sem = new Semaphore(slots, /* fair */ true); // FIFO sous contention
    }

    /** Bloquant : entre quand une place est libre. */
    public void enter() throws InterruptedException {
        sem.acquire();
    }

    /** Non bloquant : tente d'entrer immédiatement. */
    public boolean tryEnter() {
        return sem.tryAcquire();
    }

    /** Temporisé : tente d'entrer, sinon abandonne après timeout. */
    public boolean tryEnter(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        return sem.tryAcquire(timeout, unit);
    }

    /** Sortir du parking (libère 1 place). */
    public void leave() {
        sem.release();
    }

    /** Capacité totale (constante). */
    public int capacity() {
        return capacity;
    }

    /** Places libres approximatives (instantané non garanti). */
    public int approxAvailable() {
        return sem.availablePermits();
    }

    /** Taille estimée de la file d’attente (approx). */
    public int queued() {
        return sem.getQueueLength();
    }

    /** Ticket AutoCloseable pour try-with-resources. */
    public Ticket ticket() throws InterruptedException {
        enter();
        return new Ticket(this);
    }

    public static final class Ticket implements AutoCloseable {
        private Parking p;

        private Ticket(Parking p) {
            this.p = p;
        }

        @Override
        public void close() {
            if (p != null) {
                p.leave();
                p = null;
            }
        }
    }
}
