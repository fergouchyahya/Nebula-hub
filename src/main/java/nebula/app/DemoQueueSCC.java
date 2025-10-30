package nebula.app;

import nebula.core.Naming;
import nebula.queue.EventQueue;
import nebula.queue.EventQueueSCC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DemoQueueSCC {
    public static void main(String[] args) throws InterruptedException {
        EventQueue<Integer> q = new EventQueueSCC<>(3);
        ExecutorService pool = Executors.newFixedThreadPool(4, Naming.prefixedFactory("q", true));

        // 2 producteurs trop rapides
        for (int p = 0; p < 2; p++) {
            final int id = p + 1;
            pool.submit(() -> {
                for (int i = 0; i < 8; i++) {
                    try {
                        q.put(i);
                        System.out.println("P" + id + " -> put " + i + " (size=" + q.size() + ")");
                        Thread.sleep(60);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        // 2 consommateurs plus lents
        for (int c = 0; c < 2; c++) {
            final int id = c + 1;
            pool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Integer v = q.take();
                        System.out.println("C" + id + " <- took " + v + " (size=" + q.size() + ")");
                        Thread.sleep(120);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);
    }
}
