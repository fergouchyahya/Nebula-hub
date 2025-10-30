package nebula.alloc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePoolFifoTest {

    @Test
    void basic_capacity_and_acquire_release() throws InterruptedException {
        ResourcePool p = new ResourcePoolFifo(3);
        assertEquals(3, p.capacity());
        assertEquals(3, p.available());

        p.acquire(2);
        assertEquals(1, p.available());
        p.release(2);
        assertEquals(3, p.available());
    }

    @Test
    void fifo_order_is_respected_strict_with_gates() throws InterruptedException {
        // Capacité 1 + k=1 pour tous : service forcément séquentiel.
        ResourcePool p = new ResourcePoolFifo(1);
        List<Integer> serviceOrder = new ArrayList<>();

        CountDownLatch open1 = new CountDownLatch(1);
        CountDownLatch open2 = new CountDownLatch(1);
        CountDownLatch open3 = new CountDownLatch(1);

        Thread t1 = new Thread(() -> clientAcquireThenRecordThenSleepThenRelease(
                p, 1, 1, serviceOrder, open1, 80));
        Thread t2 = new Thread(() -> clientAcquireThenRecordThenSleepThenRelease(
                p, 2, 1, serviceOrder, open2, 50));
        Thread t3 = new Thread(() -> clientAcquireThenRecordThenSleepThenRelease(
                p, 3, 1, serviceOrder, open3, 50));

        // Démarre tout le monde, mais personne ne peut entrer sans "openX"
        t1.start();
        t2.start();
        t3.start();

        // Force l'ordre d'arrivée dans la file: 1 puis 2 puis 3
        open1.countDown();
        Thread.sleep(20);
        open2.countDown();
        Thread.sleep(20);
        open3.countDown();

        t1.join();
        t2.join();
        t3.join();

        assertEquals(3, serviceOrder.size());
        assertEquals(1, serviceOrder.get(0));
        assertEquals(2, serviceOrder.get(1));
        assertEquals(3, serviceOrder.get(2));
    }

    @Test
    void fifo_prevents_small_jobs_overtaking_big_job() throws InterruptedException {
        // Cas "gros devant, petits derrière" : le gros doit passer en premier (FIFO
        // stricte)
        ResourcePool p = new ResourcePoolFifo(3);
        List<Integer> serviceOrder = new ArrayList<>();

        CountDownLatch open1 = new CountDownLatch(1);
        CountDownLatch open2 = new CountDownLatch(1);
        CountDownLatch open3 = new CountDownLatch(1);

        // t1 demande 3 (tout le pool), t2 et t3 demandent 1 chacun
        Thread t1 = new Thread(() -> clientAcquireThenRecordThenSleepThenRelease(
                p, 1, 3, serviceOrder, open1, 60));
        Thread t2 = new Thread(() -> clientAcquireThenRecordThenSleepThenRelease(
                p, 2, 1, serviceOrder, open2, 30));
        Thread t3 = new Thread(() -> clientAcquireThenRecordThenSleepThenRelease(
                p, 3, 1, serviceOrder, open3, 30));

        t1.start();
        t2.start();
        t3.start();

        // On impose t1 en tête, puis t2, puis t3
        open1.countDown();
        Thread.sleep(10);
        open2.countDown();
        Thread.sleep(10);
        open3.countDown();

        t1.join();
        t2.join();
        t3.join();

        // FIFO stricte : t1 doit être servi en premier, même si sa demande est plus
        // grosse
        assertEquals(3, serviceOrder.size());
        assertEquals(1, serviceOrder.get(0));
        // L'ordre entre 2 et 3 dépendra de l’ordonnanceur (et de qui s’insère le
        // premier),
        // on ne l’asserte pas strictement ici. On vérifie juste qu’ils sont présents.
        assertTrue(serviceOrder.contains(2));
        assertTrue(serviceOrder.contains(3));
    }

    // ---------- helpers ----------
    private static void clientAcquireThenRecordThenSleepThenRelease(
            ResourcePool p, int id, int k, List<Integer> order,
            CountDownLatch gate, long holdMillis) {
        try {
            // Attend le feu vert pour contrôler précisément l'ordre d'arrivée
            gate.await();
            p.acquire(k);
            synchronized (order) {
                order.add(id);
            }
            Thread.sleep(holdMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            p.release(k);
        }
    }
}
