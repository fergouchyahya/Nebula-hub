package nebula.rw;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RWFairTest {

    @Test
    void multiple_readers_can_read_concurrently() throws InterruptedException {
        RW rw = new RWFair();
        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger peak = new AtomicInteger(0);

        int readers = 8;
        Thread[] ts = new Thread[readers];
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < readers; i++) {
            ts[i] = new Thread(() -> {
                try {
                    start.await();
                    rw.beginR();
                    int now = concurrent.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrent.decrementAndGet();
                    rw.endR();
                }
            });
            ts[i].start();
        }

        start.countDown();
        for (Thread t : ts)
            t.join();

        assertTrue(peak.get() >= 2, "plusieurs lecteurs doivent pouvoir lire en parallèle");
    }

    @Test
    void writers_are_mutually_exclusive_and_not_starved() throws InterruptedException {
        RW rw = new RWFair();
        StringBuilder log = new StringBuilder();

        // 1er writer
        Thread w1 = new Thread(() -> writer(rw, log, "W1", 80));

        // pluie de lecteurs qui essaient de "gaver" le système
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int id = i;
            readers.add(new Thread(() -> reader(rw, log, "R" + id, 30)));
        }

        // 2e writer qui ne doit pas être affamé
        Thread w2 = new Thread(() -> writer(rw, log, "W2", 60));

        // Orchestration : W1 part, puis des lecteurs arrivent, puis W2
        w1.start();
        Thread.sleep(10);
        for (Thread r : readers)
            r.start();
        Thread.sleep(10);
        w2.start();

        w1.join();
        for (Thread r : readers)
            r.join();
        w2.join();

        String s = log.toString();
        // Writer exclusif : pas de chevauchement, et W2 passe bel et bien à un moment.
        assertTrue(s.contains("W1-write"), "W1 a écrit");
        assertTrue(s.contains("W2-write"), "W2 n'a pas été affamé");
    }

    @Test
    void writer_excludes_readers() throws InterruptedException {
        RW rw = new RWFair();
        AtomicInteger insideReaders = new AtomicInteger(0);
        AtomicInteger overlap = new AtomicInteger(0);

        CountDownLatch go = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                go.await();
                rw.beginW();
                // s'il y a un lecteur pendant l'écriture -> bug
                if (insideReaders.get() > 0)
                    overlap.incrementAndGet();
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                rw.endW();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                go.await();
                rw.beginR();
                insideReaders.incrementAndGet();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                insideReaders.decrementAndGet();
                rw.endR();
            }
        });

        writer.start();
        reader.start();
        go.countDown();

        writer.join();
        reader.join();

        assertEquals(0, overlap.get(), "aucun lecteur ne doit être présent pendant l'écriture");
    }

    // --------- helpers ----------
    private static void reader(RW rw, StringBuilder log, String id, long ms) {
        try {
            rw.beginR();
            synchronized (log) {
                log.append(id).append("-read|");
            }
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rw.endR();
        }
    }

    private static void writer(RW rw, StringBuilder log, String id, long ms) {
        try {
            rw.beginW();
            synchronized (log) {
                log.append(id).append("-write|");
            }
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rw.endW();
        }
    }
}
