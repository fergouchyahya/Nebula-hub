package nebula.app;

import nebula.core.Naming;
import nebula.rw.RW;
import nebula.rw.RWFair;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DemoRWFair {
    public static void main(String[] args) {
        RW rw = new RWFair();
        ExecutorService pool = Executors.newFixedThreadPool(8, Naming.prefixedFactory("rw", true));

        // 4 lecteurs rapides
        for (int i = 0; i < 4; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    rw.beginR();
                    System.out.println("R" + id + " -> lit");
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    rw.endR();
                    System.out.println("R" + id + " <- fin");
                }
            });
        }

        // un writer
        pool.submit(() -> {
            try {
                rw.beginW();
                System.out.println("W -> écrit");
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                rw.endW();
                System.out.println("W <- fin");
            }
        });

        pool.shutdown();
    }
}
