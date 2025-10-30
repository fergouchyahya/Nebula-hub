package nebula.queue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventQueueSCCTest {

    @Test
    void basic_put_take_with_capacity() throws InterruptedException {
        EventQueue<Integer> q = new EventQueueSCC<>(2);
        assertEquals(0, q.size());

        q.put(1);
        q.put(2);
        assertEquals(2, q.size());

        int a = q.take();
        int b = q.take();
        assertEquals(0, q.size());
        assertEquals(1, a);
        assertEquals(2, b);
    }

    @Test
    void producers_block_when_full_and_resume_after_take() throws InterruptedException {
        EventQueue<Integer> q = new EventQueueSCC<>(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger step = new AtomicInteger(0);

        Thread producer = new Thread(() -> {
            try {
                q.put(42); // remplit la queue (cap=1)
                started.countDown();
                step.incrementAndGet(); // ==1
                q.put(43); // doit BLOQUER jusqu'au take
                step.incrementAndGet(); // ==2 (après déblocage)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        started.await();
        Thread.sleep(50); // laisse au 2e put le temps de se bloquer
        assertEquals(1, step.get()); // n'a pas pu faire le second put encore

        int x = q.take(); // libère la place
        assertEquals(42, x);

        producer.join(300);
        assertEquals(2, step.get()); // producteur a pu finir son 2e put
        assertEquals(1, q.size());
        assertEquals(43, q.take());
    }

    @Test
    void consumers_block_when_empty_and_resume_after_put() throws InterruptedException {
        EventQueue<Integer> q = new EventQueueSCC<>(2);
        AtomicInteger got = new AtomicInteger(-1);
        CountDownLatch started = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                started.countDown();
                int v = q.take(); // bloque ici car vide
                got.set(v);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        started.await();
        Thread.sleep(50); // s'assurer qu'il est bien bloqué
        assertEquals(-1, got.get());

        q.put(99); // débloque le consommateur

        consumer.join(300);
        assertEquals(99, got.get());
        assertEquals(0, q.size());
    }
}
