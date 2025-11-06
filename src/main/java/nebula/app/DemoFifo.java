package nebula.app;

import nebula.alloc.ResourcePool;
import nebula.alloc.ResourcePoolFifo;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DemoFifo {

    // --- Paramètres de la démo ---
    static final int CAPACITY = 3; // capacité du pool
    static final int[] KS = { 2, 1, 3, 1, 2 }; // demandes
    static final long HOLD_MS = 300; // durée "d'utilisation" de la ressource
    static final long ARRIVAL_STAGGER_MS = 0; // >0 pour jouer l'ordre d'arrivée (0 = départ simultané)

    public static void main(String[] args) throws InterruptedException {
        ResourcePool pool = new ResourcePoolFifo(CAPACITY);

        // Nomme les threads "fifo-1", "fifo-2", ...
        ExecutorService exec = Executors.newFixedThreadPool(
                KS.length,
                new ThreadFactory() {
                    final AtomicInteger c = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = Executors.defaultThreadFactory().newThread(r);
                        t.setName("fifo-" + c.getAndIncrement());
                        return t;
                    }
                });

        // Barrière de départ pour lancer tout le monde en même temps
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < KS.length; i++) {
            final int id = i + 1;
            final int k = KS[i];

            exec.submit(() -> {
                boolean got = false;
                try {
                    // Optionnel : décaler un peu l'arrivée pour tester l'ordre FIFO
                    if (ARRIVAL_STAGGER_MS > 0) {
                        Thread.sleep(ARRIVAL_STAGGER_MS * id);
                    }

                    log("T" + id + " prêt, demande " + k);
                    start.await(); // top départ commun

                    log("T" + id + " DEMANDE " + k);
                    pool.acquire(k);
                    got = true;
                    log("T" + id + " OBTIENT " + k);

                    Thread.sleep(HOLD_MS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("T" + id + " interrompu");
                } finally {
                    if (got) {
                        pool.release(k);
                        log("T" + id + " REND " + k);
                    } else {
                        log("T" + id + " annulé (pas acquis)");
                    }
                }
            });
        }

        log("TOP DEPART");
        start.countDown();

        exec.shutdown();
        if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
            exec.shutdownNow();
        }

        log("FIN DEMO");
    }

    // Horodatage court + nom du thread
    private static void log(String msg) {
        long ms = System.currentTimeMillis() % 100000;
        String th = Thread.currentThread().getName();
        System.out.printf("[%6d ms] %-8s %s%n", ms, th, msg);
    }
}
