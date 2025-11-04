package nebula.alloc;

import java.util.LinkedList;

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
        final Req r = new Req(k);
        q.addLast(r);
        try {
            for (;;) {
                if (q.peekFirst() == r && avail >= k) {
                    avail -= k;
                    q.removeFirst();
                    notifyAll(); // réveille le prochain en file (ou libère des producers)
                    return;
                }
                wait();
            }
        } catch (InterruptedException ie) {
            // IMPORTANT : se retirer de la file si on attendait encore
            if (q.remove(r))
                notifyAll();
            throw ie;
        }
    }

    @Override
    public synchronized void release(int k) {
        if (k <= 0 || k > cap)
            throw new IllegalArgumentException("0 < k ≤ capacity required");
        avail += k;
        if (avail > cap) {
            avail -= k;
            throw new IllegalStateException("avail overflow");
        }
        notifyAll();
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
