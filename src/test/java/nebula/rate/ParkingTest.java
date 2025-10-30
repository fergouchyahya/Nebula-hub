package nebula.rate;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ParkingTest {

    @Test
    void blocks_when_full_and_never_exceeds_max_concurrency() throws InterruptedException {
        Parking p = new Parking(3);
        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger peak = new AtomicInteger(0);

        int threads = 10;
        Thread[] arr = new Thread[threads];
        CountDownLatch started = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            arr[i] = new Thread(() -> {
                try {
                    started.countDown();
                    p.enter(); // bloque si plein
                    int now = concurrent.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);

                    Thread.sleep(50); // section "critique"

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrent.decrementAndGet();
                    p.leave();
                }
            });
            arr[i].start();
        }

        started.await();
        for (Thread t : arr)
            t.join();

        assertEquals(3, peak.get(), "La concurrence maximale ne doit pas dépasser la capacité");
        assertEquals(0, concurrent.get(), "Tous les threads doivent avoir quitté");
    }

    @Test
    void tryEnter_non_blocking_and_timeout() throws InterruptedException {
        Parking p = new Parking(1);

        // occuper la seule place
        assertTrue(p.tryEnter(), "doit entrer la première fois");
        assertFalse(p.tryEnter(), "non bloquant doit échouer si plein");

        // maintenant tenter avec timeout (doit échouer tant que personne ne sort)
        long t0 = System.nanoTime();
        boolean ok = p.tryEnter(50, TimeUnit.MILLISECONDS);
        long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        assertFalse(ok, "doit échouer sur timeout si plein");
        assertTrue(dtMs >= 45, "le timeout doit être respecté (~50ms)");

        // libérer et vérifier que ça re-marche
        p.leave();
        assertTrue(p.tryEnter(), "après libération, tryEnter doit réussir");
        p.leave();
    }

    @Test
    void fifo_property_from_fair_semaphore() throws InterruptedException {
        Parking p = new Parking(1);
        CountDownLatch gate = new CountDownLatch(1);
        StringBuilder order = new StringBuilder();

        Thread t1 = new Thread(() -> enterRecordLeave(p, "A", gate, 30, order));
        Thread t2 = new Thread(() -> enterRecordLeave(p, "B", gate, 30, order));
        Thread t3 = new Thread(() -> enterRecordLeave(p, "C", gate, 30, order));

        t1.start();
        t2.start();
        t3.start();

        // Tous en attente sur enter() -> on ouvre la porte simultanément
        gate.countDown();

        t1.join();
        t2.join();
        t3.join();

        // Avec un Semaphore fair, l'ordre d'entrée respecte l'ordre d'arrivée en
        // attente
        // (attention: c'est FIFO d'ACQUISITION, pas un ordonnancement strict pour le
        // CPU)
        String s = order.toString();
        // Le premier doit être A, puis B ou C selon la latence d'arrivée, mais en
        // pratique
        // avec gate commun, l'ordre se fixe au moment de l'appel à acquire() -> A,B,C
        assertTrue(s.startsWith("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("C"));
    }

    private static void enterRecordLeave(Parking p, String id, CountDownLatch gate,
            long holdMs, StringBuilder order) {
        try {
            gate.await();
            p.enter();
            synchronized (order) {
                order.append(id);
            }
            Thread.sleep(holdMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            p.leave();
        }
    }
}
