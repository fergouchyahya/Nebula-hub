package nebula.core;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class Naming {
    private Naming() {
    }

    public static ThreadFactory prefixedFactory(String prefix, boolean daemon) {
        Objects.requireNonNull(prefix, "prefix");
        AtomicLong c = new AtomicLong(1);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + c.getAndIncrement());
            t.setDaemon(daemon);
            return t;
        };
    }

    /** Variante avec handler d'exceptions et priorité. */
    public static ThreadFactory prefixedFactory(
            String prefix, boolean daemon,
            Thread.UncaughtExceptionHandler handler,
            int priority /* Thread.NORM_PRIORITY ... MAX_PRIORITY */) {

        Objects.requireNonNull(prefix, "prefix");
        AtomicLong c = new AtomicLong(1);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + c.getAndIncrement());
            t.setDaemon(daemon);
            if (handler != null)
                t.setUncaughtExceptionHandler(handler);
            if (priority != Thread.NORM_PRIORITY)
                t.setPriority(priority);
            return t;
        };
    }
}
