package nebula.ui.alloc;

import nebula.ui.core.BaseDemoPanel;
import nebula.ui.core.Step;
import nebula.ui.core.DemoMode;
import nebula.queue.EventQueue;
import nebula.queue.EventQueueSCC;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueSccPanel extends BaseDemoPanel {

    private final JTextField tfCap = new JTextField("5", 4);
    private final JTextField tfProd = new JTextField("8", 3); // en micro-step: nb d'événements put à simuler
    private final JTextField tfCons = new JTextField("8", 3); // en micro-step: nb d'événements take à simuler

    private final JProgressBar bar = new JProgressBar();
    private final JLabel lblSize = new JLabel("—");
    private final JButton microStepBtn = new JButton("Micro-step");

    private final AtomicBoolean running = new AtomicBoolean(false);
    private EventQueue<Integer> q;

    // ===== Simulation locale (sans threads) =====
    private boolean simInit = false;
    private int simCap, simSize;
    private int simProdBudget, simConsBudget;
    private int simNextVal = 0; // valeur produite croissante
    private int simNP, simNC; // nb d'IDs prod/cons pour logs
    private int simNextPid = 1, simNextCid = 1;

    // files d'attente FIFO de producteurs/consommateurs bloqués
    private final java.util.ArrayDeque<Integer> pWait = new java.util.ArrayDeque<>(); // IDs producteurs en attente
    private final java.util.ArrayDeque<Integer> cWait = new java.util.ArrayDeque<>(); // IDs consommateurs en attente

    public QueueSccPanel() {
        super("Queue SCC (prod/cons borné)");

        // UI
        controlsPanel().add(new JLabel("Cap:"));
        controlsPanel().add(tfCap);
        controlsPanel().add(new JLabel("Prod:"));
        controlsPanel().add(tfProd);
        controlsPanel().add(new JLabel("Cons:"));
        controlsPanel().add(tfCons);
        controlsPanel().add(microStepBtn);

        bar.setStringPainted(true);
        extraNorth().add(titled(bar, "Occupation de la file"));
        add(lblSize, BorderLayout.SOUTH);

        microStepBtn.addActionListener(e -> runSimulationStep());

        // Script pas-à-pas (Init -> Simulation -> Observe/Stop)
        setSteps(List.of(
                new Step() {
                    public String id() {
                        return "init";
                    }

                    public String title() {
                        return "Initialiser la file bornée";
                    }

                    public String descriptionHtml() {
                        return """
                                <ul>
                                  <li>Création d’une <b>EventQueueSCC</b> de capacité <i>Cap</i>.</li>
                                  <li>La jauge montre la taille courante de la file (0..Cap).</li>
                                </ul>""";
                    }

                    public void perform() {
                        int cap = readInt(tfCap, 1);
                        q = new EventQueueSCC<>(cap);
                        ui(() -> {
                            bar.setMaximum(cap);
                            bar.setValue(0);
                            bar.setString("0 / " + cap);
                            lblSize.setText("Taille: 0/" + cap);
                        });
                        logln("[Init] cap=" + cap);
                    }
                },
                new Step() {
                    public String id() {
                        return "simulate";
                    }

                    public String title() {
                        return "Simulation manuelle (micro-step)";
                    }

                    public String descriptionHtml() {
                        return """
                                <p>Clique sur <b>Micro-step</b> pour exécuter les événements un à un :</p>
                                <ul>
                                  <li><b>Prod</b> = nombre d'événements <code>put()</code> à tenter</li>
                                  <li><b>Cons</b> = nombre d'événements <code>take()</code> à tenter</li>
                                  <li>Blocages gérés en <i>FIFO stricte</i> (prod attend si file pleine, cons attend si vide)</li>
                                </ul>
                                <p>Simulation <i>déterministe</i> sans threads.</p>""";
                    }

                    public void perform() {
                        ensureQueue();
                        initSimulation();
                        logln("[Mode] Simulation pas-à-pas activée — clique sur Micro-step.");
                    }
                },
                new Step() {
                    public String id() {
                        return "observe-stop";
                    }

                    public String title() {
                        return "Observer puis Stop";
                    }

                    public String descriptionHtml() {
                        return "<p>Observation brève, puis arrêt.</p>";
                    }

                    public void perform() throws Exception {
                        Thread.sleep(1500);
                        stopDemo();
                    }
                }));
    }

    // ===== MICRO-STEP =====

    private void initSimulation() {
        simCap = readInt(tfCap, 1);
        simProdBudget = readInt(tfProd, 0);
        simConsBudget = readInt(tfCons, 0);
        simNP = Math.max(1, Math.min(16, readInt(tfProd, 1))); // IDs producteurs (pour logs)
        simNC = Math.max(1, Math.min(16, readInt(tfCons, 1))); // IDs consommateurs (pour logs)
        simSize = 0;
        simNextVal = 0;
        simNextPid = 1;
        simNextCid = 1;
        pWait.clear();
        cWait.clear();
        simInit = true;

        ui(() -> {
            bar.setMaximum(simCap);
            bar.setValue(0);
            bar.setString("0 / " + simCap);
            lblSize.setText("Taille: 0/" + simCap);
        });
        logln("[Sim:init] cap=" + simCap + " prodBudget=" + simProdBudget + " consBudget=" + simConsBudget);
    }

    private void runSimulationStep() {
        ensureQueue();
        if (!simInit) {
            initSimulation();
            return;
        }

        // Priorité: débloquer si possible (équité FIFO stricte)
        // 1) Si des consommateurs attendent ET la file a des éléments, servir d'abord
        // un consumer en attente
        if (!cWait.isEmpty() && simSize > 0) {
            int cid = cWait.removeFirst();
            int val = (--simSize >= 0) ? simNextVal /* dummy read */ : simNextVal; // val fictive
            logln("C" + cid + " <- take " + val + " (unpark)");
            updateSizeGauges();
            return;
        }
        // 2) Si des producteurs attendent ET il reste de la place, insérer un
        // producteur en attente
        if (!pWait.isEmpty() && simSize < simCap) {
            int pid = pWait.removeFirst();
            int val = simNextVal++;
            simSize++;
            logln("P" + pid + " -> put " + val + " (unpark)");
            updateSizeGauges();
            return;
        }

        // 3) Émettre un nouvel événement (arrivée) : on alterne production puis
        // consommation si budgets restants
        if (simProdBudget > 0) {
            int pid = nextPid();
            if (simSize < simCap) {
                int val = simNextVal++;
                simSize++;
                simProdBudget--;
                logln("P" + pid + " -> put " + val);
                updateSizeGauges();
                return;
            } else {
                // plein: producteur bloque
                pWait.addLast(pid);
                simProdBudget--; // l'arrivée a eu lieu, il attendra
                logln("P" + pid + " WAIT (plein, size=" + simSize + ")");
                updateSizeGauges();
                return;
            }
        }

        if (simConsBudget > 0) {
            int cid = nextCid();
            if (simSize > 0) {
                int val = --simSize >= 0 ? simNextVal /* dummy read */ : simNextVal;
                simConsBudget--;
                logln("C" + cid + " <- take " + val);
                updateSizeGauges();
                return;
            } else {
                // vide: consommateur bloque
                cWait.addLast(cid);
                simConsBudget--; // l'arrivée a eu lieu, il attendra
                logln("C" + cid + " WAIT (vide, size=" + simSize + ")");
                updateSizeGauges();
                return;
            }
        }

        // 4) Plus d'événements à créer; essayer encore de débloquer (au cas où)
        if (!cWait.isEmpty() && simSize > 0) {
            int cid = cWait.removeFirst();
            int val = (--simSize >= 0) ? simNextVal : simNextVal;
            logln("C" + cid + " <- take " + val + " (unpark)");
            updateSizeGauges();
            return;
        }
        if (!pWait.isEmpty() && simSize < simCap) {
            int pid = pWait.removeFirst();
            int val = simNextVal++;
            simSize++;
            logln("P" + pid + " -> put " + val + " (unpark)");
            updateSizeGauges();
            return;
        }

        logln("[Sim] Terminé — plus d’événements.");
        updateSizeGauges();
    }

    private int nextPid() {
        int id = simNextPid;
        simNextPid = (simNextPid % Math.max(1, simNP)) + 1;
        return id;
    }

    private int nextCid() {
        int id = simNextCid;
        simNextCid = (simNextCid % Math.max(1, simNC)) + 1;
        return id;
    }

    private void updateSizeGauges() {
        int cap = simCap;
        int size = simSize;
        ui(() -> {
            bar.setMaximum(cap);
            bar.setValue(size);
            bar.setString(size + " / " + cap);
            lblSize.setText("Taille: " + size + "/" + cap +
                    "  |  Pwait=" + pWait.size() + "  Cwait=" + cWait.size());
        });
    }

    // ===== FULL RUN (threads) inchangé =====

    @Override
    public void startDemo() {
        if (mode == DemoMode.STEP_BY_STEP)
            return;
        ensureExecs();
        if (!running.compareAndSet(false, true))
            return;

        try {
            int cap = readInt(tfCap, 1);
            q = new EventQueueSCC<>(cap);
            ui(() -> {
                bar.setMaximum(cap);
                bar.setValue(0);
                bar.setString("0 / " + cap);
                lblSize.setText("Taille: 0/" + cap);
            });
            logln("[Start] cap=" + cap + " P=" + readInt(tfProd, 1) + " C=" + readInt(tfCons, 1));

            int np = readInt(tfProd, 1);
            for (int p = 0; p < np; p++) {
                final int pid = p + 1;
                exec.submit(() -> {
                    int i = 0;
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            q.put(i);
                            logln("P" + pid + " -> put " + i);
                            i++;
                            Thread.sleep(60);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return null;
                });
            }
            scheduleSizeTicker();

            int nc = readInt(tfCons, 1);
            for (int c = 0; c < nc; c++) {
                final int cid = c + 1;
                exec.submit(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            Integer v = q.take();
                            logln("C" + cid + " <- take " + v);
                            Thread.sleep(120);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return null;
                });
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Valeurs invalides.");
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
        q = null;
        ui(() -> {
            bar.setValue(0);
            bar.setString("—");
            lblSize.setText("—");
        });
        // reset simulation locale
        simInit = false;
        pWait.clear();
        cWait.clear();
        simSize = 0;
    }

    private void scheduleSizeTicker() {
        ensureExecs();
        tick.scheduleAtFixedRate(() -> {
            int size = (q != null) ? q.size() : 0;
            int cap = bar.getMaximum();
            ui(() -> {
                bar.setValue(size);
                bar.setString(size + " / " + cap);
                lblSize.setText("Taille: " + size + "/" + cap);
            });
        }, 0, 120, TimeUnit.MILLISECONDS);
    }

    // --- helpers ---
    private void ensureQueue() {
        if (q == null) {
            int cap = readInt(tfCap, 1);
            q = new EventQueueSCC<>(cap);
            ui(() -> {
                bar.setMaximum(cap);
                bar.setValue(0);
                bar.setString("0 / " + cap);
                lblSize.setText("Taille: 0/" + cap);
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
}
