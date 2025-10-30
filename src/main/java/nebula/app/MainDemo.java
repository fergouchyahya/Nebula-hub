package nebula.app;

import nebula.alloc.ResourcePool;
import nebula.alloc.ResourcePoolDirect;
import nebula.core.Naming;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainDemo {
    public static void main(String[] args) {
        ResourcePool pool = new ResourcePoolDirect(3);
        ExecutorService exec = Executors.newFixedThreadPool(
                5, Naming.prefixedFactory("worker", true));

        for (int i = 0; i < 5; i++) {
            final int id = i + 1;
            exec.submit(() -> {
                try {
                    pool.acquire(1);
                    System.out.println("T" + id + " -> acquired");
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    pool.release(1);
                    System.out.println("T" + id + " <- released");
                }
            });
        }
        exec.shutdown();
    }
}
