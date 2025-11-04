package nebula.app;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Demos {

    private static final Map<String, Runnable> DEMOS = new LinkedHashMap<>();

    static {
        // Démo FIFO (pool de ressources)
        DEMOS.put("fifo", () -> {
            System.out.println("\n=== DEMO: fifo ===");
            DemoFifo.main(new String[0]);
            sleep(1200); // petit tampon pour laisser finir les prints
        });

        // Démo Parking (sémaphore fair)
        DEMOS.put("parking", () -> {
            System.out.println("\n=== DEMO: parking ===");
            try {
                DemoParking.main(new String[0]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sleep(500);
        });

        // Démo Queue (lock + conditions)
        DEMOS.put("queue", () -> {
            System.out.println("\n=== DEMO: queue ===");
            try {
                DemoQueueSCC.main(new String[0]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sleep(300);
        });

        // Démo Readers-Writers équitable
        DEMOS.put("rw", () -> {
            System.out.println("\n=== DEMO: rw ===");
            DemoRWFair.main(new String[0]);
            // DemoRWFair ne bloque pas : on laisse respirer un peu
            sleep(500);
        });

        // Démo ResourcePoolDirect (moniteur simple)
        DEMOS.put("direct", () -> {
            System.out.println("\n=== DEMO: direct ===");
            MainDemo.main(new String[0]);
            sleep(500);
        });
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usageAndExit();
        }
        String mode = args[0].trim().toLowerCase();

        if ("all".equals(mode)) {
            DEMOS.forEach((name, r) -> safeRun(name, r));
            System.out.println("\n=== ALL DEMOS DONE ===");
            return;
        }

        Runnable r = DEMOS.get(mode);
        if (r == null) {
            System.err.println("Unknown demo: " + mode);
            usageAndExit();
        } else {
            safeRun(mode, r);
        }
    }

    private static void safeRun(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            System.err.println("Demo '" + name + "' failed: " + t);
            t.printStackTrace(System.err);
        }
    }

    private static void usageAndExit() {
        System.out.println("Usage: run --args=\"<demo>\"");
        System.out.println("  <demo> ∈ { fifo | parking | queue | rw | direct | all }");
        System.exit(1);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
