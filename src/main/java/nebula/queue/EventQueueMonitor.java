package nebula.queue;

import java.util.LinkedList;

public class EventQueueMonitor<T> implements EventQueue<T> {
    private final LinkedList<T> q = new LinkedList<>();
    private final int capacity;

    public EventQueueMonitor(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException("capacity>0 required");
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(T item) throws InterruptedException {
        if (item == null)
            throw new NullPointerException("item");
        while (q.size() >= capacity) {
            wait(); // spurious wakeups => while
        }
        q.addLast(item);
        notifyAll(); // réveille un éventuel consommateur en attente
    }

    @Override
    public synchronized T take() throws InterruptedException {
        while (q.isEmpty()) {
            wait();
        }
        T x = q.removeFirst();
        notifyAll(); // réveille un éventuel producteur bloqué sur "plein"
        return x;
    }

    @Override
    public synchronized int size() {
        return q.size();
    }
}
