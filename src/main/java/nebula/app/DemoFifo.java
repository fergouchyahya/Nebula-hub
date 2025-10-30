package nebula.app;

import nebula.alloc.ResourcePool;
import nebula.alloc.ResourcePoolFifo;
import nebula.core.Naming;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DemoFifo {
    public static void main(String[] args) {
        ResourcePool pool = new ResourcePoolFifo(3);
        ExecutorService exec = Executors.newFixedThreadPool(
                5, Naming.prefixedFactory("fifo", true));

        int[] ks = { 2, 1, 3, 1, 2 }; // demandes variées pour voir l'effet FIFO
        for (int i = 0; i < ks.length; i++) {
            final int id = i + 1;
            final int k = ks[i];
            exec.submit(() -> {
                try {
                    System.out.println("T" + id + " demande " + k);
                    pool.acquire(k);
                    System.out.println("T" + id + " OBTIENT " + k);
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    pool.release(k);
                    System.out.println("T" + id + " REND " + k);
                }
            });
        }
        exec.shutdown();
    }
}
