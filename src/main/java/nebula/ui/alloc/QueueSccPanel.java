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
    private final JTextField tfProd = new JTextField("2", 3);
    private final JTextField tfCons = new JTextField("2", 3);

    private final JProgressBar bar = new JProgressBar();
    private final JLabel lblSize = new JLabel("—");

    private final AtomicBoolean running = new AtomicBoolean(false);
    private EventQueue<Integer> q;

    public QueueSccPanel() {
        super("Queue SCC (prod/cons borné)");
        controlsPanel().add(new JLabel("Cap:"));
        controlsPanel().add(tfCap);
        controlsPanel().add(new JLabel("Prod:"));
        controlsPanel().add(tfProd);
        controlsPanel().add(new JLabel("Cons:"));
        controlsPanel().add(tfCons);
        bar.setStringPainted(true);
        extraNorth().add(titled(bar, "Occupation de la file"));
        add(lblSize, BorderLayout.SOUTH);

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
                                  <li>Préparation du graphe d’occupation et du journal.</li>
                                </ul>
                                """;
                    }

                    public void perform() {
                        int cap = readInt(tfCap, 1);
                        q = new EventQueueSCC<>(cap);
                        ui(() -> {
                            bar.setMaximum(cap);
                            bar.setValue(0);
                            lblSize.setText("Taille: 0/" + cap);
                        });
                        logln("[Init] cap=" + cap);
                    }
                },
                new Step() {
                    public String id() {
                        return "producers";
                    }

                    public String title() {
                        return "Lancer les producteurs";
                    }

                    public String descriptionHtml() {
                        return """
                                <p>Des producteurs publient périodiquement <code>put()</code>.</p>
                                """;
                    }

                    public void perform() {
                        ensureExecs();
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
                    }
                },
                new Step() {
                    public String id() {
                        return "consumers";
                    }

                    public String title() {
                        return "Lancer les consommateurs";
                    }

                    public String descriptionHtml() {
                        return """
                                <p>Des consommateurs retirent via <code>take()</code>.</p>
                                """;
                    }

                    public void perform() {
                        ensureExecs();
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
                        return "<p>Observation courte, puis arrêt.</p>";
                    }

                    public void perform() throws Exception {
                        Thread.sleep(1500);
                        stopDemo();
                    }
                }));
    }

    protected void configureControls(JPanel controls) {
        controls.add(new JLabel("Cap:"));
        controls.add(tfCap);
        controls.add(new JLabel("Prod:"));
        controls.add(tfProd);
        controls.add(new JLabel("Cons:"));
        controls.add(tfCons);
    }

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
            /* ok */ }
        shutdownExecs();
        logln("[Stop]");
    }

    @Override
    public void resetDemo() {
        stopDemo();
        log.setText("");
        bar.setValue(0);
        lblSize.setText("—");
    }

    private void scheduleSizeTicker() {
        ensureExecs();
        tick.scheduleAtFixedRate(() -> {
            int size = (q != null) ? q.size() : 0;
            ui(() -> {
                bar.setValue(size);
                int max = bar.getMaximum();
                lblSize.setText("Taille: " + size + "/" + max);
            });
        }, 0, 120, TimeUnit.MILLISECONDS);
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
