package nebula.rw;

import java.util.concurrent.Semaphore;

/**
 * Lecteurs–Rédacteurs équitable (tourniquet fair) :
 * - Pas de famine pour les writers.
 * - Regroupement de lecteurs quand aucun writer n'est en train d'écrire ni en
 * file devant eux.
 * - Ingrédients : turnstile (fair), roomEmpty (fair), rmutex pour protéger
 * readCount.
 */
public class RWFair implements RW {

    private final Semaphore turnstile = new Semaphore(1, true); // ordre d'arrivée
    private final Semaphore roomEmpty = new Semaphore(1, true); // salle libre pour writer / bloquée par 1er reader
    private final Object rmutex = new Object(); // protège readCount
    private int readCount = 0;

    @Override
    public void beginR() throws InterruptedException {
        // Passe par le tourniquet pour respecter les writers en attente,
        // mais on le libère immédiatement pour permettre le batching des readers.
        turnstile.acquire();
        turnstile.release();

        // Section critique sur readCount
        synchronized (rmutex) {
            readCount++;
            if (readCount == 1) {
                // le premier lecteur verrouille la salle (empêche writers)
                roomEmpty.acquire();
            }
        }
    }

    @Override
    public void endR() {
        synchronized (rmutex) {
            readCount--;
            if (readCount == 0) {
                // le dernier lecteur libère la salle
                roomEmpty.release();
            }
        }
    }

    @Override
    public void beginW() throws InterruptedException {
        // Le writer entre dans la file et GARDE le tourniquet
        turnstile.acquire();
        // Il attend que la salle soit vide (zéro lecteurs et zéro writer)
        roomEmpty.acquire();
        // -> écriture
    }

    @Override
    public void endW() {
        // Fin d'écriture : libère la salle puis relève le tourniquet
        roomEmpty.release();
        turnstile.release();
    }
}
