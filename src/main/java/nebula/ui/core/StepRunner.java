// nebula/ui/core/StepRunner.java
package nebula.ui.core;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class StepRunner {
    private final List<Step> steps;
    private int idx = 0;
    private final boolean[] done; // mémorise si perform() a déjà été exécuté

    public StepRunner(List<Step> steps) {
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.done = new boolean[this.steps.size()];
    }

    public int size() {
        return steps.size();
    }

    public int index() {
        return idx;
    }

    public boolean hasNext() {
        return idx < steps.size() - 1;
    }

    public Step current() {
        return steps.get(idx);
    }

    // Navigation SEULEMENT
    public boolean next() {
        if (idx < steps.size() - 1) {
            idx++;
            return true;
        }
        return false;
    }

    public boolean prev() {
        if (idx > 0) {
            idx--;
            return true;
        }
        return false;
    }

    // Exécution explicite
    public void performCurrent() throws Exception {
        if (!done[idx]) {
            steps.get(idx).perform();
            done[idx] = true;
        }
    }
}
