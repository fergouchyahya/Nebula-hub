package nebula.queue;

import java.util.LinkedList;

public class EventQueueMonitor<T> implements EventQueue<T> {
    private final LinkedList<T> q = new LinkedList<T>();
    private final int capacity;

    public EventQueueMonitor(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void put(T item) throws InterruptedException {
        while (capacity > 0 && q.size() >= capacity) {
            wait();
        }
        q.addLast(item);
        notifyAll();
    }

    @Override
    public T take() throws InterruptedException {
        while (q.isEmpty()) {
            wait();
        }
        T x =q.removeFirst();
        notifyAll();
        return x;
    }

    @Override
    public int size() {
        return q.size();
    }

}
