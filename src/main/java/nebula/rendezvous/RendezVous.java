package nebula.rendezvous;

/**
 * Barrière réutilisable (rendez-vous) pour n threads.
 * Contrat : chaque appel à await() bloque jusqu'à ce que n threads soient
 * arrivés.
 * Réutilisable par "générations" successives.
 */
public class RendezVous {
    private final int n;
    private int arrived = 0;
    private int generation = 0; // identifiant du cycle courant

    public RendezVous(int n) {
        if (n <= 0)
            throw new IllegalArgumentException("n>0 required");
        this.n = n;
    }

    public synchronized void await() throws InterruptedException {
        final int g = generation; // capture la génération courante
        arrived++;
        if (arrived == n) {
            // Dernier arrivé : on bascule à la génération suivante et on libère tout le
            // monde
            generation++;
            arrived = 0;
            notifyAll();
            return;
        }

        // Attente tant qu'on est encore dans la même génération
        try {
            while (g == generation) {
                wait(); // spurious wakeups => boucle
            }
        } catch (InterruptedException ie) {
            // Si on est toujours dans la même génération, on se retire pour ne pas bloquer
            // le groupe
            if (g == generation) {
                arrived--;
                // Optionnel : si plus personne n’attend après ce retrait, réveiller pour
                // rechecker
                notifyAll();
            }
            throw ie;
        }
    }
}
