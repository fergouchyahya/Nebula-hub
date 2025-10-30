package nebula.alloc;

import java.util.LinkedList;
import java.util.Objects;

public class ResourcePoolFifo implements ResourcePool {

    private static final class Req {
        final int k;

        Req(int k) {
            this.k = k;
        }
    }

    private final int cap;
    private int avail;
    private final LinkedList<Req> q = new LinkedList<>();

    public ResourcePoolFifo(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException("capacity>0 required");
        this.cap = capacity;
        this.avail = capacity;
    }

    @Override
    public synchronized void acquire(int k) throws InterruptedException {
        if (k <= 0 || k > cap)
            throw new IllegalArgumentException("0 < k ≤ capacity required");
        Req r = new Req(k);
        q.addLast(r);
        while (q.peekFirst() != r || avail < k) {
            wait(); // <= inside synchronized
        }
        avail -= k;
        q.removeFirst();
        notifyAll(); // <= inside synchronized
    }

    @Override
    public synchronized void release(int k) {
        if (k <= 0 || k > cap)
            throw new IllegalArgumentException("0 < k ≤ capacity required");
        avail += k;
        if (avail > cap)
            throw new IllegalStateException("avail overflow");
        notifyAll(); // <= inside synchronized
    }

    @Override
    public synchronized int capacity() {
        return cap;
    }

    @Override
    public synchronized int available() {
        return avail;
    }
}
