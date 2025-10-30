package nebula.app;

import nebula.core.Naming;
import nebula.rate.Parking;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DemoParking {
    public static void main(String[] args) throws InterruptedException {
        int slots = 8;
        Parking parking = new Parking(slots);

        ExecutorService pool = Executors.newFixedThreadPool(16, Naming.prefixedFactory("job", true));

        for (int i = 0; i < 50; i++) {
            final int id = i + 1;
            pool.submit(() -> {
                try {
                    parking.enter();
                    System.out.println("Task " + id + " ENTER (avail~" + parking.approxAvailable() + ")");
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    parking.leave();
                    System.out.println("Task " + id + " LEAVE (queue~" + parking.queued() + ")");
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Done.");
    }
}
