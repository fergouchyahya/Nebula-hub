package nebula.queue;

public interface EventQueue<T> {
    void put(T item) throws InterruptedException;

    T take() throws InterruptedException;

    int size();

}
