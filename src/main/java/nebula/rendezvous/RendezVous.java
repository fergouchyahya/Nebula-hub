package nebula.rendezvous;

public class RendezVous {
    private final int n;
    private int arrived=0;
    
    public RendezVous(int n) {
        if (n <= 0)
            throw new IllegalArgumentException();
        this.n = n;
    }
    
    public synchronized void await() throws InterruptedException {
        arrived++;
        if (arrived < n) {
            wait();
        } else {
            arrived = 0;
            notifyAll();
        }
    }

}
