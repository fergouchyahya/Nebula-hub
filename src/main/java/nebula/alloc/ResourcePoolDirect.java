package nebula.alloc;

public class ResourcePoolDirect implements ResourcePool {
    private final int cap;
    private int avail;

    public ResourcePoolDirect(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException("capacity>0 required");
        this.cap = capacity;
        this.avail = capacity;
    }

    @Override
    public synchronized void acquire(int k) throws InterruptedException {
        if (k <= 0 || k > cap)
            throw new IllegalArgumentException("0 < k ≤ capacity required");
        while (avail < k) {
            wait(); // réveils spurious possibles => while
        }
        avail -= k;
        // invariant: 0 ≤ avail ≤ cap
    }

    @Override
    public synchronized void release(int k) {
        if (k <= 0 || k > cap)
            throw new IllegalArgumentException("0 < k ≤ capacity required");
        avail += k;
        if (avail > cap) { // protection contre sur-liberation
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
