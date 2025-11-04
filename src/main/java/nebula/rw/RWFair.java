package nebula.rw;

import java.util.concurrent.Semaphore;

public class RWFair implements RW {
    private final Semaphore turnstile = new Semaphore(1, true); // ordre d'arrivée
    private final Semaphore roomEmpty = new Semaphore(1, true); // exclusivité writers / groupe lecteurs
    private final Object rmutex = new Object(); // protège readCount
    private int readCount = 0;

    @Override
    public void beginR() throws InterruptedException {
        // Respecte les writers en attente
        turnstile.acquire();
        turnstile.release();

        boolean first;
        // Incrémente d'abord, puis si premier => verrouille la salle
        synchronized (rmutex) {
            first = (readCount++ == 0);
        }
        if (first) {
            try {
                roomEmpty.acquire();
            } catch (InterruptedException ie) {
                // rollback si on n'a pas pu verrouiller la salle
                synchronized (rmutex) {
                    readCount--;
                }
                throw ie;
            }
        }
    }

    @Override
    public void endR() {
        boolean last = false;
        synchronized (rmutex) {
            if (readCount <= 0)
                throw new IllegalStateException("endR without beginR");
            last = (--readCount == 0);
        }
        if (last) {
            roomEmpty.release();
        }
    }

    @Override
    public void beginW() throws InterruptedException {
        // Le writer prend le tourniquet et le garde jusqu'à la fin d'écriture
        turnstile.acquire();
        try {
            roomEmpty.acquire();
        } catch (InterruptedException ie) {
            // relâcher le tourniquet si on n'entre pas
            turnstile.release();
            throw ie;
        }
    }

    @Override
    public void endW() {
        roomEmpty.release();
        turnstile.release();
    }
}
