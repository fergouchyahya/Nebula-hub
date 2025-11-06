package nebula.ui.alloc;

import nebula.ui.core.BaseDemoPanel;
import nebula.ui.core.Step;
import nebula.ui.core.DemoMode;
import nebula.alloc.ResourcePool;
import nebula.alloc.ResourcePoolFifo;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AllocFifoPanel extends BaseDemoPanel {

    private final JTextField tfCap = new JTextField("3", 4);
    private final JProgressBar bar = new JProgressBar();
    private final JLabel lblAvail = new JLabel("—");
    private final JButton microStepBtn = new JButton("Micro-step"); // bouton pour le pas-à-pas

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ResourcePool pool;

    // --- Simulation locale ---
    private boolean simInit = false;
    private int simCap, simAvail;
    private int simIdx = 0;
    private final int[] simKs = { 2, 1, 3, 1, 2, 1, 1, 3, 2, 1 };
    private final java.util.ArrayDeque<Req> simWait = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Run> simRun = new java.util.ArrayDeque<>();

    public AllocFifoPanel() {
        super("Allocateur FIFO");

        // --- UI setup ---
        controlsPanel().add(new JLabel("Capacity:"));
        controlsPanel().add(tfCap);
        controlsPanel().add(microStepBtn); // on ajoute le bouton
        bar.setStringPainted(true);
        extraNorth().add(titled(bar, "Ressources utilisées"));
        add(lblAvail, BorderLayout.SOUTH);

        // Bouton micro-step : avance la simulation
        microStepBtn.addActionListener(e -> runSimulationStep());

        // Script pas-à-pas “macro” (Init → Simulation → Stop)
        setSteps(List.of(
                new Step() {
                    public String id() {
                        return "init";
                    }

                    public String title() {
                        return "Initialiser l’allocateur FIFO";
                    }

                    public String descriptionHtml() {
                        return """
                                    <ul>
                                      <li>Création d’un <b>ResourcePoolFifo</b> de capacité <i>cap</i>.</li>
                                      <li>La jauge indique le nombre de ressources <i>occupées</i>.</li>
                                    </ul>
                                """;
                    }

                    public void perform() {
                        int cap = readInt(tfCap, 1);
                        pool = new ResourcePoolFifo(cap);
                        ui(() -> {
                            bar.setMaximum(cap);
                            bar.setValue(0);
                            bar.setString("0 / " + cap);
                            lblAvail.setText("Disponible: " + cap + "/" + cap);
                        });
                        logln("[Init] cap=" + cap);
                    }
                },
                new Step() {
                    public String id() {
                        return "spawn";
                    }

                    public String title() {
                        return "Simulation manuelle";
                    }

                    public String descriptionHtml() {
                        return """
                                    <p>Clique sur <b>Micro-step</b> pour exécuter les événements un à un :</p>
                                    <ul>
                                      <li>Demande de ressources</li>
                                      <li>Obtention (si possible)</li>
                                      <li>Rendu</li>
                                    </ul>
                                    <p>Politique : FIFO stricte, simulation déterministe sans threads.</p>
                                """;
                    }

                    public void perform() {
                        ensurePool();
                        ensureExecs();
                        logln("[Mode] Simulation pas-à-pas activée — clique sur le bouton Micro-step.");
                    }
                },
                new Step() {
                    public String id() {
                        return "observe-stop";
                    }

                    public String title() {
                        return "Observer puis arrêter";
                    }

                    public String descriptionHtml() {
                        return "<p>On observe brièvement, puis Stop.</p>";
                    }

                    public void perform() throws Exception {
                        Thread.sleep(1500);
                        stopDemo();
                    }
                }));
    }

    // ====== MÉTHODE DE SIMULATION ======
    private void runSimulationStep() {
        ensurePool();
        ensureExecs();

        if (!simInit) {
            simCap = pool.capacity();
            simAvail = simCap;
            simWait.clear();
            simRun.clear();
            simIdx = 0;
            simInit = true;
            logln("[Sim:init] cap=" + simCap);
            updateSimGauges();
            return;
        }

        // Étape 1 : demande
        if (simIdx < simKs.length) {
            int id = simIdx + 1;
            int k = Math.min(simKs[simIdx], simCap);
            simIdx++;
            simWait.addLast(new Req(id, k));
            logln("T" + id + " demande " + k);
            updateSimGauges();
            return;
        }

        // Étape 2 : obtention
        if (!simWait.isEmpty()) {
            Req head = simWait.peekFirst();
            if (head.k <= simAvail) {
                simWait.removeFirst();
                simAvail -= head.k;
                logln("T" + head.id + " OBTIENT " + head.k);
                simRun.addLast(new Run(head.id, head.k, 1)); // tick = durée de “travail”
                updateSimGauges();
                return;
            }
        }

        // Étape 3 : rendu
        if (!simRun.isEmpty()) {
            Run r = simRun.peekFirst();
            if (r.ticks > 0) {
                r.ticks--;
                updateSimGauges();
                return;
            } else {
                simRun.removeFirst();
                simAvail += r.k;
                logln("T" + r.id + " REND " + r.k);
                updateSimGauges();
                return;
            }
        }

        logln("[Sim] Terminé — plus d’événements.");
        updateSimGauges();
    }

    private void updateSimGauges() {
        int used = simCap - simAvail;
        ui(() -> {
            bar.setMaximum(simCap);
            bar.setValue(used);
            bar.setString(used + " / " + simCap);
            lblAvail.setText("Disponible: " + simAvail + "/" + simCap);
        });
    }

    // ====== FULL RUN ======
    @Override
    public void startDemo() {
        if (mode == DemoMode.STEP_BY_STEP)
            return;
        ensureExecs();
        if (!running.compareAndSet(false, true))
            return;

        try {
            int cap = readInt(tfCap, 1);
            pool = new ResourcePoolFifo(cap);
            ui(() -> {
                bar.setMaximum(cap);
                bar.setValue(0);
                bar.setString("0 / " + cap);
                lblAvail.setText("Disponible: " + cap + "/" + cap);
            });
            logln("[Start] ResourcePoolFifo cap=" + cap);

            int[] ks = { 2, 1, 3, 1, 2, 1, 1, 3, 2, 1 };
            for (int i = 0; i < ks.length; i++) {
                final int id = i + 1;
                final int k = Math.min(ks[i], cap);
                exec.submit(() -> {
                    boolean got = false;
                    try {
                        logln("T" + id + " demande " + k);
                        pool.acquire(k);
                        got = true;
                        logln("T" + id + " OBTIENT " + k);
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (got) {
                            pool.release(k);
                            logln("T" + id + " REND " + k);
                        } else {
                            logln("T" + id + " annulé (pas acquis)");
                        }
                    }
                });
            }
            scheduleGauges();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Capacity invalide.");
            running.set(false);
        }
    }

    @Override
    public void stopDemo() {
        if (!running.getAndSet(false) && mode == DemoMode.FULL_RUN) {
            /* noop */ }
        shutdownExecs();
        logln("[Stop]");
    }

    @Override
    public void resetDemo() {
        stopDemo();
        log.setText("");
        ui(() -> {
            bar.setValue(0);
            bar.setString("—");
            lblAvail.setText("—");
        });
        pool = null;
        simInit = false;
    }

    // ===== Utilitaires =====
    private void scheduleGauges() {
        ensureExecs();
        tick.scheduleAtFixedRate(() -> {
            if (pool == null)
                return;
            int avail = pool.available();
            int cap = pool.capacity();
            int used = cap - avail;
            ui(() -> {
                bar.setMaximum(cap);
                bar.setValue(used);
                bar.setString(used + " / " + cap);
                lblAvail.setText("Disponible: " + avail + "/" + cap);
            });
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void ensurePool() {
        if (pool == null) {
            int cap = readInt(tfCap, 1);
            pool = new ResourcePoolFifo(cap);
            ui(() -> {
                bar.setMaximum(cap);
                bar.setValue(0);
                bar.setString("0 / " + cap);
                lblAvail.setText("Disponible: " + cap + "/" + cap);
            });
            logln("[Auto-init] cap=" + cap);
        }
    }

    private int readInt(JTextField tf, int min) {
        int v = Integer.parseInt(tf.getText().trim());
        return Math.max(min, v);
    }

    private JPanel titled(JComponent c, String title) {
        var p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    // --- Simulation DTOs ---
    private static final class Req {
        final int id, k;

        Req(int id, int k) {
            this.id = id;
            this.k = k;
        }
    }

    private static final class Run {
        final int id, k;
        int ticks;

        Run(int id, int k, int ticks) {
            this.id = id;
            this.k = k;
            this.ticks = ticks;
        }
    }
}
