package nebula.alloc;

public interface ResourcePool {
    void acquire(int k) throws InterruptedException;

    void release(int k);

    int capacity();

    int available();

}
