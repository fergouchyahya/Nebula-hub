package nebula.ui.alloc;

import nebula.ui.core.BaseDemoPanel;
import nebula.ui.core.Step;
import nebula.ui.core.DemoMode;
import nebula.rate.Parking;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

public class ParkingPanel extends BaseDemoPanel {

    private final JTextField tfSlots = new JTextField("8", 4);
    private final JTextField tfTasks = new JTextField("50", 5);

    private final JLabel lblConc = new JLabel("—");
    private final JProgressBar barConc = new JProgressBar();
    private final JButton microStepBtn = new JButton("Micro-step"); // pas-à-pas déterministe

    private final AtomicInteger concurrent = new AtomicInteger(0);
    private final AtomicInteger peak = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Parking parking;

    // --- Simulation locale (sans threads) ---
    private boolean simInit = false;
    private int simSlots, simAvail, simNextId, simSpawnLeft;
    private int simPeak = 0;
    private final java.util.ArrayDeque<Req> simWait = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Run> simRun = new java.util.ArrayDeque<>();

    public ParkingPanel() {
        super("Parking (limiteur de débit)");

        // UI
        controlsPanel().add(new JLabel("Slots:"));
        controlsPanel().add(tfSlots);
        controlsPanel().add(new JLabel("Tasks:"));
        controlsPanel().add(tfTasks);
        controlsPanel().add(microStepBtn); // bouton micro-step

        barConc.setStringPainted(true);
        extraNorth().add(titled(barConc, "Concurrence instantanée (threads dans la section)"));
        add(lblConc, BorderLayout.SOUTH);

        // Micro-step : avance d’un événement (arrivée / entrée / travail / sortie)
        microStepBtn.addActionListener(e -> runSimulationStep());

        // Script (init -> simulation manuelle -> observe+stop)
        setSteps(List.of(
                new Step() {
                    public String id() {
                        return "init";
                    }

                    public String title() {
                        return "Initialiser le Parking";
                    }

                    public String descriptionHtml() {
                        return """
                                <ul>
                                  <li>Création d’un <b>Parking</b> avec <i>Slots</i> permissions.</li>
                                  <li>La jauge montre le nombre d’entrées <i>actives</i>.</li>
                                </ul>""";
                    }

                    public void perform() {
                        int slots = readInt(tfSlots, 1);
                        parking = new Parking(slots);
                        concurrent.set(0);
                        peak.set(0);
                        ui(() -> {
                            barConc.setMaximum(slots);
                            barConc.setValue(0);
                            barConc.setString("0 / " + slots);
                            lblConc.setText("Concurrent: 0  |  Max: 0");
                        });
                        logln("[Init] slots=" + slots);
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
                                  <li>Arrivée d’une tâche (mise en file d’attente)</li>
                                  <li>Entrée si slot disponible (FIFO stricte)</li>
                                  <li>Travail (1 tick)</li>
                                  <li>Sortie (libération de slot)</li>
                                </ul>
                                <p>Politique : FIFO stricte, simulation déterministe <i>sans threads</i>.</p>""";
                    }

                    public void perform() {
                        ensureParking();
                        initSimulationState();
                        logln("[Mode] Simulation pas-à-pas activée — clique sur Micro-step.");
                    }
                },
                new Step() {
                    public String id() {
                        return "observe-stop";
                    }

                    public String title() {
                        return "Observer et Stop";
                    }

                    public String descriptionHtml() {
                        return "<p>Observation brève, puis arrêt propre.</p>";
                    }

                    public void perform() throws Exception {
                        Thread.sleep(1500);
                        stopDemo();
                    }
                }));
    }

    // ====================== SIMULATION LOCALE (pas-à-pas) ======================

    private void initSimulationState() {
        try {
            simSlots = Math.max(1, Integer.parseInt(tfSlots.getText().trim()));
        } catch (NumberFormatException e) {
            simSlots = 1;
        }
        try {
            // Nombre total de tâches à faire arriver au fil des pas
            simSpawnLeft = Math.max(1, Integer.parseInt(tfTasks.getText().trim()));
        } catch (NumberFormatException e) {
            simSpawnLeft = 10;
        }
        simAvail = simSlots;
        simNextId = 1;
        simPeak = 0;
        simWait.clear();
        simRun.clear();
        simInit = true;

        ui(() -> {
            barConc.setMaximum(simSlots);
            barConc.setValue(0);
            barConc.setString("0 / " + simSlots);
            lblConc.setText("Concurrent: 0  |  Max: 0");
        });
        logln("[Sim:init] slots=" + simSlots + " tasks=" + simSpawnLeft);
    }

    private void runSimulationStep() {
        ensureParking();
        if (!simInit) {
            initSimulationState();
            return;
        }

        // Étape 1 : Arrivée (jusqu’à épuisement du quota simSpawnLeft)
        if (simSpawnLeft > 0) {
            int id = simNextId++;
            simSpawnLeft--;
            simWait.addLast(new Req(id)); // 1 permission par tâche
            logln("Task " + id + " ARRIVE (wait=" + simWait.size() + ")");
            updateSimGauges();
            return;
        }

        // Étape 2 : Entrée si possible (FIFO stricte)
        if (!simWait.isEmpty()) {
            if (simAvail > 0) {
                Req r = simWait.removeFirst();
                simAvail -= 1;
                simRun.addLast(new Run(r.id, 1)); // 1 tick de "travail"
                int now = simSlots - simAvail;
                simPeak = Math.max(simPeak, now);
                logln("Task " + r.id + " ENTER (conc=" + now + ")");
                updateSimGauges();
                return;
            }
        }

        // Étape 3 : Travail / Sortie
        if (!simRun.isEmpty()) {
            Run run = simRun.peekFirst();
            if (run.ticks > 0) {
                run.ticks--;
                updateSimGauges();
                return;
            } else {
                simRun.removeFirst();
                simAvail += 1;
                int now = simSlots - simAvail;
                logln("Task " + run.id + " LEAVE (conc=" + now + ")");
                updateSimGauges();
                return;
            }
        }

        logln("[Sim] Terminé — plus d’événements.");
        updateSimGauges();
    }

    private void updateSimGauges() {
        int used = simSlots - simAvail;
        ui(() -> {
            barConc.setMaximum(simSlots);
            barConc.setValue(used);
            barConc.setString(used + " / " + simSlots);
            lblConc.setText("Concurrent: " + used + "  |  Max: " + Math.max(simPeak, used));
        });
    }

    // ====================== MODE FULL RUN (multi-threads) ======================

    @Override
    public void startDemo() {
        if (mode == DemoMode.STEP_BY_STEP)
            return;
        ensureExecs();
        if (!running.compareAndSet(false, true))
            return;

        try {
            final int slots = readInt(tfSlots, 1);
            final int tasks = readInt(tfTasks, 1);

            parking = new Parking(slots);
            concurrent.set(0);
            peak.set(0);

            ui(() -> {
                barConc.setMaximum(slots);
                barConc.setValue(0);
                barConc.setString("0 / " + slots);
                lblConc.setText("Concurrent: 0  |  Max: 0");
            });

            logln("[Start] slots=" + slots + " tasks=" + tasks);

            for (int i = 0; i < tasks; i++) {
                final int id = i + 1;
                exec.submit(() -> {
                    boolean got = false;
                    try {
                        parking.enter();
                        got = true;
                        int now = concurrent.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);
                        ui(() -> {
                            barConc.setValue(now);
                            barConc.setString(now + " / " + barConc.getMaximum());
                            lblConc.setText("Concurrent: " + now + "  |  Max: " + peak.get());
                        });
                        logln("Task " + id + " ENTER (conc=" + now + ")");
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (got) {
                            int now = concurrent.decrementAndGet();
                            parking.leave();
                            ui(() -> {
                                barConc.setValue(now);
                                barConc.setString(now + " / " + barConc.getMaximum());
                                lblConc.setText("Concurrent: " + now + "  |  Max: " + peak.get());
                            });
                            logln("Task " + id + " LEAVE (conc=" + now + ")");
                        } else {
                            logln("Task " + id + " annulée (pas entrée)");
                        }
                    }
                });
            }

            // Mise à jour périodique (au cas où)
            tick.scheduleAtFixedRate(() -> {
                int now = concurrent.get();
                ui(() -> {
                    barConc.setValue(now);
                    barConc.setString(now + " / " + barConc.getMaximum());
                    lblConc.setText("Concurrent: " + now + "  |  Max: " + peak.get());
                });
            }, 0, 100, TimeUnit.MILLISECONDS);

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
        concurrent.set(0);
        peak.set(0);
        ui(() -> {
            barConc.setValue(0);
            barConc.setString("—");
            lblConc.setText("—");
        });
        parking = null;

        // reset simulation locale
        simInit = false;
        simWait.clear();
        simRun.clear();
    }

    // ====================== Utilitaires ======================

    private void ensureParking() {
        if (parking == null) {
            int slots = readInt(tfSlots, 1);
            parking = new Parking(slots);
            ui(() -> {
                barConc.setMaximum(slots);
                barConc.setValue(0);
                barConc.setString("0 / " + slots);
                lblConc.setText("Concurrent: 0  |  Max: 0");
            });
            logln("[Auto-init] slots=" + slots);
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

    // --- DTOs simulation ---
    private static final class Req {
        final int id;

        Req(int id) {
            this.id = id;
        }
    }

    private static final class Run {
        final int id;
        int ticks;

        Run(int id, int ticks) {
            this.id = id;
            this.ticks = ticks;
        }

        Run(int id) {
            this(id, 1);
        }
    }
}
