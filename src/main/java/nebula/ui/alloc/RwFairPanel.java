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

    private final AtomicInteger readers = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private RW rw;

    public RwFairPanel() {
        super("Lecteurs–Rédacteurs (Fair)");
        controlsPanel().add(new JLabel("Readers:"));
        controlsPanel().add(tfReaders);
        controlsPanel().add(new JLabel("Writers:"));
        controlsPanel().add(tfWriters);
        add(lblReaders, BorderLayout.SOUTH);

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
                                  <li>Remise à zéro du compteur de lecteurs.</li>
                                </ul>
                                """;
                    }

                    public void perform() {
                        rw = new RWFair();
                        readers.set(0);
                        ui(() -> lblReaders.setText("Lecteurs actifs: 0"));
                        logln("[Init] RWFair prêt");
                    }
                },
                new Step() {
                    public String id() {
                        return "readers";
                    }

                    public String title() {
                        return "Lancer les lecteurs";
                    }

                    public String descriptionHtml() {
                        return """
                                <p>Lecteurs alternent <code>beginR()</code> / lecture / <code>endR()</code>.</p>
                                """;
                    }

                    public void perform() {
                        ensureExecs();
                        ensureRw();
                        int nr = readInt(tfReaders, 1);
                        for (int i = 0; i < nr; i++) {
                            final int id = i + 1;
                            exec.submit(() -> {
                                while (!Thread.currentThread().isInterrupted()) {
                                    try {
                                        rw.beginR();
                                        int now = readers.incrementAndGet();
                                        ui(() -> lblReaders.setText("Lecteurs actifs: " + now));
                                        logln("R" + id + " -> lit");
                                        Thread.sleep(60);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        readers.decrementAndGet();
                                        rw.endR();
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
                    }
                },
                new Step() {
                    public String id() {
                        return "writers";
                    }

                    public String title() {
                        return "Lancer les rédacteurs";
                    }

                    public String descriptionHtml() {
                        return """
                                <p>Rédacteurs alternent <code>beginW()</code> / écriture / <code>endW()</code> (accès exclusif).</p>
                                """;
                    }

                    public void perform() {
                        ensureExecs();
                        ensureRw();
                        int nw = readInt(tfWriters, 1);
                        for (int i = 0; i < nw; i++) {
                            final int id = i + 1;
                            exec.submit(() -> {
                                while (!Thread.currentThread().isInterrupted()) {
                                    try {
                                        rw.beginW();
                                        logln("W" + id + " -> écrit");
                                        Thread.sleep(120);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        rw.endW();
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

    protected void configureControls(JPanel controls) {
        controls.add(new JLabel("Readers:"));
        controls.add(tfReaders);
        controls.add(new JLabel("Writers:"));
        controls.add(tfWriters);
    }

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
            ui(() -> lblReaders.setText("Lecteurs actifs: 0"));
            logln("[Start] RWFair R=" + nr + " W=" + nw);

            // Lecteurs
            for (int i = 0; i < nr; i++) {
                final int id = i + 1;
                exec.submit(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            rw.beginR();
                            int now = readers.incrementAndGet();
                            ui(() -> lblReaders.setText("Lecteurs actifs: " + now));
                            logln("R" + id + " -> lit");
                            Thread.sleep(60);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            readers.decrementAndGet();
                            rw.endR();
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
                        try {
                            rw.beginW();
                            logln("W" + id + " -> écrit");
                            Thread.sleep(120);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            rw.endW();
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
        ui(() -> lblReaders.setText("Lecteurs actifs: 0"));
        rw = null;
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
}
