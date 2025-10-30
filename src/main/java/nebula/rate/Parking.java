package nebula.rate;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Limiteur de concurrence (max N tâches simultanées).
 * Utilise un Semaphore "fair" pour garantir l'ordre FIFO d'entrée.
 */
public class Parking {
    private final Semaphore sem;

    public Parking(int slots) {
        if (slots <= 0)
            throw new IllegalArgumentException("slots > 0 required");
        this.sem = new Semaphore(slots, true); // équitable => FIFO d'acquisition
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
        return sem.tryAcquire(timeout, unit);
    }

    /** Sortir du parking (libère 1 place). */
    public void leave() {
        sem.release();
    }

    /** Places totales. */
    public int capacity() {
        return sem.availablePermits() + (int) sem.getQueueLength();
    }

    /** Approximation des places libres (peut varier juste après l'appel). */
    public int approxAvailable() {
        return sem.availablePermits();
    }

    /** Longueur de file d'attente (approx). */
    public int queued() {
        return sem.getQueueLength();
    }
}
