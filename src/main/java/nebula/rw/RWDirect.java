package nebula.rw;

public class RWDirect implements RW {
    private int nr = 0;
    private boolean writting = false;

    @Override
    public synchronized void beginR() throws InterruptedException {
        while (writting) {
            wait();
        }
        nr++;
    }

    @Override
    public synchronized void endR() {
        nr--;
        if (nr == 0) {
            notifyAll();
        }
        ;
    }

    @Override
    public synchronized void beginW() throws InterruptedException {
        while (writting || nr > 0) {
            wait();
        }
        writting = true;
    }

    @Override
    public synchronized void endW() {
        writting = false;
        notifyAll();
    }

}
