package nebula.rate;

import java.util.concurrent.Semaphore;

public class ParkingSemaphore {
    private final Semaphore sem;

    public ParkingSemaphore(int slots) {
        this.sem = new Semaphore(slots, true);
    }

    public void enter() throws InterruptedException {
        sem.acquire();
    }

    public void leave() {
        sem.release();
    }
}
