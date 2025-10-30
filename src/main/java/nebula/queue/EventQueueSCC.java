package nebula.queue;

import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class EventQueueSCC<T> implements EventQueue<T> {
    private final ArrayDeque<T> q;
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public EventQueueSCC(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException("capacity>0 required");
        this.capacity = capacity;
        this.q = new ArrayDeque<>(capacity);
    }

    @Override
    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (capacity == q.size()) {
                notFull.await();
            }
            q.addLast(item);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (q.isEmpty()) {
                notEmpty.await();
            }
            T x = q.removeFirst();
            notFull.signal();
            return x;
        } finally {
            lock.unlock();

        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return q.size();
        } finally {
            lock.unlock();
        }
    }

}
