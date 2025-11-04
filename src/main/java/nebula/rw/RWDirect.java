package nebula.rw;

public class RWDirect implements RW {
    private int nr = 0; // lecteurs actifs
    private int waitingW = 0; // writers en attente
    private boolean writing = false;

    @Override
    public synchronized void beginR() throws InterruptedException {
        // Bloquer l'arrivée de nouveaux lecteurs si des writers attendent
        while (writing || waitingW > 0) {
            wait();
        }
        nr++;
    }

    @Override
    public synchronized void endR() {
        if (nr <= 0)
            throw new IllegalStateException("endR without beginR");
        nr--;
        if (nr == 0)
            notifyAll();
    }

    @Override
    public synchronized void beginW() throws InterruptedException {
        waitingW++;
        try {
            while (writing || nr > 0) {
                wait();
            }
            writing = true;
        } finally {
            waitingW--;
        }
    }

    @Override
    public synchronized void endW() {
        writing = false;
        notifyAll();
    }
}
