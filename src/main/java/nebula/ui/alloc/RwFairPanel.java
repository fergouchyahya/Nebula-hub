package nebula.ui.alloc;

import nebula.ui.core.BaseDemoPanel;
import nebula.ui.core.Step;
import nebula.ui.core.DemoMode;
import nebula.rw.RW;
import nebula.rw.RWFair;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RwFairPanel extends BaseDemoPanel {

    private final JTextField tfReaders = new JTextField("6", 3);
    private final JTextField tfWriters = new JTextField("2", 3);

    private final JLabel lblReaders = new JLabel("Lecteurs actifs: 0");
    private final JLabel lblState = new JLabel("État: —");
    private final JButton microStepBtn = new JButton("Micro-step");

    private final AtomicInteger readers = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private RW rw;

    // ======= Simulation locale (sans threads) =======
    private boolean simInit = false;
    private int budgetReadersArrivals, budgetWritersArrivals;
    private int activeReaders = 0;
    private boolean writerActive = false;

    // files d’attente FIFO (IDs symboliques)
    private final java.util.ArrayDeque<Integer> rWait = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Integer> wWait = new java.util.ArrayDeque<>();
    private int nextRid = 1, nextWid = 1;

    // exécutions en cours (ticks)
    private final java.util.ArrayDeque<RunR> rRun = new java.util.ArrayDeque<>();
    private RunW wRun = null;

    public RwFairPanel() {
        super("Lecteurs–Rédacteurs (Fair)");

        // UI
        controlsPanel().add(new JLabel("Readers:"));
        controlsPanel().add(tfReaders);
        controlsPanel().add(new JLabel("Writers:"));
        controlsPanel().add(tfWriters);
        controlsPanel().add(microStepBtn);

        var south = new JPanel(new GridLayout(2, 1));
        south.add(lblReaders);
        south.add(lblState);
        add(south, BorderLayout.SOUTH);

        microStepBtn.addActionListener(e -> runSimulationStep());

        setSteps(List.of(
                new Step() {
                    public String id() {
                        return "init";
                    }

                    public String title() {
                        return "Initialiser RWFair";
                    }

                    public String descriptionHtml() {
                        return """
                                <ul>
                                  <li>Création d’un verrou <b>RWFair</b> (évite la famine).</li>
                                  <li>Remise à zéro des compteurs.</li>
                                </ul>""";
                    }

                    public void perform() {
                        rw = new RWFair();
                        readers.set(0);
                        ui(() -> {
                            lblReaders.setText("Lecteurs actifs: 0");
                            lblState.setText("État: prêt");
                        });
                        logln("[Init] RWFair prêt");
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
                                  <li>Arrivées de lecteurs et rédacteurs (budgets d'événements)</li>
                                  <li>Déblocages <i>FIFO</i> (fair) : si un rédacteur attend, aucun nouveau lecteur ne démarre</li>
                                  <li>Exécution (1 tick) puis fin de lecture/écriture</li>
                                </ul>
                                <p>Simulation <i>déterministe</i> sans threads.</p>""";
                    }

                    public void perform() {
                        ensureRw();
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
                        return "<p>Observation courte, puis arrêt propre.</p>";
                    }

                    public void perform() throws Exception {
                        Thread.sleep(2000);
                        stopDemo();
                    }
                }));
    }

    // ==================== MICRO-STEP ====================

    private void initSimulation() {
        budgetReadersArrivals = readInt(tfReaders, 0);
        budgetWritersArrivals = readInt(tfWriters, 0);
        activeReaders = 0;
        writerActive = false;
        rWait.clear();
        wWait.clear();
        rRun.clear();
        wRun = null;
        nextRid = 1;
        nextWid = 1;
        simInit = true;
        ui(() -> {
            lblReaders.setText("Lecteurs actifs: 0");
            lblState.setText("État: Rwait=0 Wwait=0 | Wactive=false");
        });
        logln("[Sim:init] R=" + budgetReadersArrivals + " W=" + budgetWritersArrivals);
    }

    private void runSimulationStep() {
        ensureRw();
        if (!simInit) {
            initSimulation();
            return;
        }

        // 1) Déblocage prioritaire (politique fair simple):
        // - Si pas d’écrivain actif et file Wwait non vide => démarrer 1 writer
        if (!writerActive && !wWait.isEmpty() && activeReaders == 0) {
            int wid = wWait.removeFirst();
            writerActive = true;
            wRun = new RunW(wid, 1);
            logln("W" + wid + " -> beginW (unpark)");
            updateState();
            return;
        }
        // - Sinon, si aucun writer n’attend ET pas d’écrivain actif, débloquer lecteurs
        // en attente
        if (!writerActive && wWait.isEmpty() && !rWait.isEmpty()) {
            int rid = rWait.removeFirst();
            activeReaders++;
            rRun.addLast(new RunR(rid, 1));
            logln("R" + rid + " -> beginR (unpark)");
            updateState();
            return;
        }

        // 2) Arrivées (création d'événements) — on sert d’abord un writer si possible
        if (budgetWritersArrivals > 0) {
            int wid = nextWid++;
            budgetWritersArrivals--;
            // Writer peut démarrer seulement si aucun lecteur ni writer actif
            if (!writerActive && activeReaders == 0) {
                writerActive = true;
                wRun = new RunW(wid, 1);
                logln("W" + wid + " -> beginW");
            } else {
                wWait.addLast(wid);
                logln("W" + wid + " WAIT");
            }
            updateState();
            return;
        }

        if (budgetReadersArrivals > 0) {
            int rid = nextRid++;
            budgetReadersArrivals--;
            // Si un writer attend ou actif, le lecteur NE démarre PAS (fair), il attend
            if (!writerActive && wWait.isEmpty()) {
                activeReaders++;
                rRun.addLast(new RunR(rid, 1));
                logln("R" + rid + " -> beginR");
            } else {
                rWait.addLast(rid);
                logln("R" + rid + " WAIT");
            }
            updateState();
            return;
        }

        // 3) Avancement des exécutions
        // - Writer en cours ?
        if (writerActive && wRun != null) {
            if (wRun.ticks > 0) {
                wRun.ticks--;
                updateState();
                return;
            } else {
                int wid = wRun.id;
                wRun = null;
                writerActive = false;
                logln("W" + wid + " -> endW");
                updateState();
                return;
            }
        }

        // - Lecteurs en cours ?
        if (!rRun.isEmpty()) {
            RunR r = rRun.peekFirst();
            if (r.ticks > 0) {
                r.ticks--;
                updateState();
                return;
            } else {
                rRun.removeFirst();
                activeReaders = Math.max(0, activeReaders - 1);
                logln("R" + r.id + " -> endR");
                updateState();
                return;
            }
        }

        logln("[Sim] Terminé — plus d’événements.");
        updateState();
    }

    private void updateState() {
        ui(() -> {
            lblReaders.setText("Lecteurs actifs: " + activeReaders);
            lblState.setText("État: Rwait=" + rWait.size() + " Wwait=" + wWait.size()
                    + " | Wactive=" + writerActive);
        });
    }

    // ==================== FULL RUN (threads) ====================

    @Override
    public void startDemo() {
        if (mode == DemoMode.STEP_BY_STEP)
            return;
        ensureExecs();
        if (!running.compareAndSet(false, true))
            return;

        try {
            int nr = readInt(tfReaders, 1);
            int nw = readInt(tfWriters, 1);
            rw = new RWFair();
            readers.set(0);
            ui(() -> {
                lblReaders.setText("Lecteurs actifs: 0");
                lblState.setText("État: exécution multi-threads");
            });
            logln("[Start] RWFair R=" + nr + " W=" + nw);

            // Lecteurs
            for (int i = 0; i < nr; i++) {
                final int id = i + 1;
                exec.submit(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        boolean got = false;
                        try {
                            rw.beginR();
                            got = true;
                            int now = readers.incrementAndGet();
                            ui(() -> lblReaders.setText("Lecteurs actifs: " + now));
                            logln("R" + id + " -> lit");
                            Thread.sleep(60);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            if (got) {
                                int now = readers.decrementAndGet();
                                rw.endR();
                                ui(() -> lblReaders.setText("Lecteurs actifs: " + Math.max(now, 0)));
                            } else {
                                logln("R" + id + " annulé (pas de lecture)");
                            }
                        }
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return null;
                });
            }

            // Rédacteurs
            for (int i = 0; i < nw; i++) {
                final int id = i + 1;
                exec.submit(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        boolean got = false;
                        try {
                            rw.beginW();
                            got = true;
                            logln("W" + id + " -> écrit");
                            Thread.sleep(120);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            if (got) {
                                rw.endW();
                            } else {
                                logln("W" + id + " annulé (pas d’écriture)");
                            }
                        }
                        try {
                            Thread.sleep(80);
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
            /* ok */ }
        shutdownExecs();
        logln("[Stop]");
    }

    @Override
    public void resetDemo() {
        stopDemo();
        log.setText("");
        readers.set(0);
        ui(() -> {
            lblReaders.setText("Lecteurs actifs: 0");
            lblState.setText("État: —");
        });
        rw = null;

        // reset simulation
        simInit = false;
        rWait.clear();
        wWait.clear();
        rRun.clear();
        wRun = null;
        activeReaders = 0;
        writerActive = false;
    }

    private void ensureRw() {
        if (rw == null) {
            rw = new RWFair();
            readers.set(0);
            ui(() -> lblReaders.setText("Lecteurs actifs: 0"));
            logln("[Auto-init] RWFair prêt");
        }
    }

    private int readInt(JTextField tf, int min) {
        int v = Integer.parseInt(tf.getText().trim());
        return Math.max(min, v);
    }

    // DTOs simulation
    private static final class RunR {
        final int id;
        int ticks;

        RunR(int id, int ticks) {
            this.id = id;
            this.ticks = ticks;
        }

        RunR(int id) {
            this(id, 1);
        }
    }

    private static final class RunW {
        final int id;
        int ticks;

        RunW(int id, int ticks) {
            this.id = id;
            this.ticks = ticks;
        }

        RunW(int id) {
            this(id, 1);
        }
    }
}
