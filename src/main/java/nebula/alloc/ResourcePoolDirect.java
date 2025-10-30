package nebula.alloc;

public class ResourcePoolDirect implements ResourcePool {
    private int cap;
    private int avail;

    public ResourcePoolDirect(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        this.cap = capacity;
        this.avail = capacity;
    }

    @Override
    public void acquire(int k) throws InterruptedException {
        while (avail < k) {
            wait();
        }
        avail -= k;
    }

    @Override
    public void release(int k) {
        avail += k;
        notifyAll();

    }

    @Override
    public int capacity() {
        return cap;
    }

    @Override
    public int available() {
        return avail;
    }

}
