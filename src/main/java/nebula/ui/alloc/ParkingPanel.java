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

public class ParkingPanel extends BaseDemoPanel {

    private final JTextField tfSlots = new JTextField("8", 4);
    private final JTextField tfTasks = new JTextField("50", 5);

    private final JLabel lblConc = new JLabel("—");
    private final JProgressBar barConc = new JProgressBar();

    private final AtomicInteger concurrent = new AtomicInteger(0);
    private final AtomicInteger peak = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Parking parking;

    public ParkingPanel() {
        super("Parking (limiteur de débit)");
        controlsPanel().add(new JLabel("Slots:"));
        controlsPanel().add(tfSlots);
        controlsPanel().add(new JLabel("Tasks:"));
        controlsPanel().add(tfTasks);
        barConc.setStringPainted(true);
        extraNorth().add(titled(barConc, "Concurrence instantanée (threads dans la section)"));
        add(lblConc, BorderLayout.SOUTH);

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
                                      <li>Réinitialisation des compteurs.</li>
                                    </ul>
                                """;
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
                            lblConc.setText("Concurrent max: 0");
                        });
                        logln("[Init] slots=" + slots);
                    }
                },
                new Step() {
                    public String id() {
                        return "spawn";
                    }

                    public String title() {
                        return "Lancer un lot de tâches";
                    }

                    public String descriptionHtml() {
                        return """
                                    <p>Chaque tâche fait <code>enter()</code>, travaille un peu, puis <code>leave()</code>.</p>
                                """;
                    }

                    public void perform() {
                        ensureExecs();
                        ensureParking();
                        final int slots = readInt(tfSlots, 1);
                        final int tasks = readInt(tfTasks, 1);
                        logln("[Spawn] tasks=" + tasks);

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
                                        barConc.setString(now + " / " + slots);
                                        lblConc.setText("Concurrent max: " + peak.get());
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
                                            lblConc.setText("Concurrent max: " + peak.get());
                                        });
                                        logln("Task " + id + " LEAVE (conc=" + now + ")");
                                    } else {
                                        logln("Task " + id + " annulée (pas entrée)");
                                    }
                                }
                            });
                        }
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
                        Thread.sleep(2000);
                        stopDemo();
                    }
                }));
    }

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
                lblConc.setText("Concurrent max: 0");
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
                            lblConc.setText("Concurrent max: " + peak.get());
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
                                lblConc.setText("Concurrent max: " + peak.get());
                            });
                            logln("Task " + id + " LEAVE (conc=" + now + ")");
                        } else {
                            logln("Task " + id + " annulée (pas entrée)");
                        }
                    }
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
        concurrent.set(0);
        peak.set(0);
        ui(() -> {
            barConc.setValue(0);
            barConc.setString("—");
            lblConc.setText("—");
        });
        parking = null;
    }

    private void ensureParking() {
        if (parking == null) {
            int slots = readInt(tfSlots, 1);
            parking = new Parking(slots);
            ui(() -> {
                barConc.setMaximum(slots);
                barConc.setValue(0);
                barConc.setString("0 / " + slots);
                lblConc.setText("Concurrent max: 0");
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
}
