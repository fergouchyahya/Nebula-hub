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

    // UI
    private final JTextField tfCap = new JTextField("3", 4);
    private final JProgressBar bar = new JProgressBar();
    private final JLabel lblAvail = new JLabel("—");

    // État
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ResourcePool pool;

    public AllocFifoPanel() {
        super("Allocateur FIFO");
        controlsPanel().add(new JLabel("Capacity:"));
        controlsPanel().add(tfCap);
        bar.setStringPainted(true);
        // Place la jauge sous les barres via le hook prévu
        extraNorth().add(titled(bar, "Ressources utilisées"));
        add(lblAvail, BorderLayout.SOUTH);

        // Script pas-à-pas
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
                        return "Lancer des tâches avec demandes k variées";
                    }

                    public String descriptionHtml() {
                        return """
                                <p>On lance une séquence de tâches qui demandent <code>k</code> ressources,
                                   travaillent brièvement, puis <code>release(k)</code>.</p>
                                <p><i>Observation :</i> l’ordre respecte la FIFO.</p>
                                """;
                    }

                    public void perform() {
                        ensureExecs(); // IMPORTANT
                        ensurePool();
                        int cap = pool.capacity();
                        int[] ks = { 2, 1, 3, 1, 2, 1, 1, 3, 2, 1 };
                        for (int i = 0; i < ks.length; i++) {
                            final int id = i + 1;
                            final int k = Math.min(ks[i], cap);
                            exec.submit(() -> {
                                try {
                                    logln("T" + id + " demande " + k);
                                    pool.acquire(k);
                                    logln("T" + id + " OBTIENT " + k);
                                    Thread.sleep(250);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    pool.release(k);
                                    logln("T" + id + " REND " + k);
                                }
                                return null;
                            });
                        }
                        scheduleGauges();
                    }
                },
                new Step() {
                    public String id() {
                        return "observe-stop";
                    }

                    public String title() {
                        return "Observer puis arrêter proprement";
                    }

                    public String descriptionHtml() {
                        return "<p>On observe brièvement, puis Stop.</p>";
                    }

                    public void perform() throws Exception {
                        Thread.sleep(2000);
                        stopDemo();
                    }
                }));
    }

    
    protected void configureControls(JPanel controls) {
        controls.add(new JLabel("Capacity:"));
        controls.add(tfCap);
    }

    // ===== Full run =====
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
                    try {
                        logln("T" + id + " demande " + k);
                        pool.acquire(k);
                        logln("T" + id + " OBTIENT " + k);
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        pool.release(k);
                        logln("T" + id + " REND " + k);
                    }
                    return null;
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
            /* ok */ }
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
    }

    // ===== Utilitaires =====
    private void scheduleGauges() {
        ensureExecs(); // IMPORTANT
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
}
