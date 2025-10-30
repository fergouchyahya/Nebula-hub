package nebula.core;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class Naming {
    private Naming() {
    };

    public static ThreadFactory prefixedFactory(String prefix, boolean daemon) {
        AtomicLong c = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + c.getAndIncrement());
            t.setDaemon(daemon);
            return t;
        };

    }

}
