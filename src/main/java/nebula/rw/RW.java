package nebula.rw;

public interface RW {
    void beginR() throws InterruptedException;

    void endR();

    void beginW() throws InterruptedException;

    void endW();
}
