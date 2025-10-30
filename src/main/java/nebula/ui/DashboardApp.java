package nebula.ui;

import nebula.alloc.ResourcePool;
import nebula.alloc.ResourcePoolFifo;
import nebula.queue.EventQueue;
import nebula.queue.EventQueueSCC;
import nebula.rate.Parking;
import nebula.rw.RW;
import nebula.rw.RWFair;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dashboard visuel simple pour démos concurrentes (Swing pur).
 * Onglets:
 * - FIFO Alloc
 * - Queue SCC
 * - Parking (limiteur)
 * - Lecteurs–Rédacteurs (RWFair)
 *
 * Conçu pour être compact et sans dépendances.
 */
public class DashboardApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DashboardApp::createAndShow);
    }

    private static void createAndShow() {
        JFrame f = new JFrame("Nebula Hub — Démos Concurrence");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(1000, 700);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Alloc FIFO", new AllocFifoPanel());
        tabs.addTab("Queue SCC", new QueueSccPanel());
        tabs.addTab("Parking", new ParkingPanel());
        tabs.addTab("R/W Fair", new RwFairPanel());

        f.setContentPane(tabs);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    // ---------- Outils UI ----------

    static JPanel titled(JComponent c, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new TitledBorder(title));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    static JTextArea newLog() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return ta;
    }

    static void log(JTextArea ta, String msg) {
        SwingUtilities.invokeLater(() -> {
            ta.append(msg + "\n");
            ta.setCaretPosition(ta.getDocument().getLength());
        });
    }

    static JButton btn(String label, Runnable r) {
        JButton b = new JButton(label);
        b.addActionListener(e -> r.run());
        return b;
    }

    // ---------- Onglet 1 : Allocateur FIFO ----------

    static class AllocFifoPanel extends JPanel {
        private final JTextArea log = newLog();
        private final JProgressBar bar = new JProgressBar();
        private final JLabel lblAvail = new JLabel("—");
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService exec;
        private ScheduledExecutorService tick;
        private ResourcePool pool;

        AllocFifoPanel() {
            super(new BorderLayout(8, 8));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JTextField tfCap = new JTextField("3", 4);
            top.add(new JLabel("Capacity:"));
            top.add(tfCap);

            JButton start = btn("Start", () -> {
                if (running.get())
                    return;
                try {
                    int cap = Math.max(1, Integer.parseInt(tfCap.getText().trim()));
                    pool = new ResourcePoolFifo(cap);
                    bar.setMaximum(cap);
                    running.set(true);
                    exec = Executors.newFixedThreadPool(6);
                    tick = Executors.newSingleThreadScheduledExecutor();
                    log.setText("");
                    log(log, "[Start] ResourcePoolFifo cap=" + cap);

                    // taches avec demandes variées pour visualiser la FIFO
                    int[] ks = { 2, 1, 3, 1, 2, 1, 1, 3, 2, 1 };
                    for (int i = 0; i < ks.length; i++) {
                        final int id = i + 1;
                        final int k = Math.min(ks[i], cap);
                        exec.submit(() -> {
                            try {
                                log(log, "T" + id + " demande " + k);
                                pool.acquire(k);
                                log(log, "T" + id + " OBTIENT " + k);
                                Thread.sleep(250);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                pool.release(k);
                                log(log, "T" + id + " REND " + k);
                            }
                        });
                    }

                    // rafraîchissement UI dispo
                    tick.scheduleAtFixedRate(() -> {
                        int avail = pool.available();
                        SwingUtilities.invokeLater(() -> {
                            bar.setValue(pool.capacity() - avail);
                            lblAvail.setText("Disponible: " + avail + "/" + pool.capacity());
                        });
                    }, 0, 100, TimeUnit.MILLISECONDS);

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Capacity invalide.");
                }
            });

            JButton stop = btn("Stop", this::stopAll);

            top.add(start);
            top.add(stop);
            add(top, BorderLayout.NORTH);

            JPanel mid = new JPanel(new BorderLayout());
            bar.setStringPainted(true);
            mid.add(titled(bar, "Ressources utilisées"), BorderLayout.NORTH);
            mid.add(titled(new JScrollPane(log), "Journal"), BorderLayout.CENTER);
            mid.add(lblAvail, BorderLayout.SOUTH);
            add(mid, BorderLayout.CENTER);
        }

        private void stopAll() {
            if (!running.getAndSet(false))
                return;
            if (exec != null)
                exec.shutdownNow();
            if (tick != null)
                tick.shutdownNow();
            log(log, "[Stop]");
        }
    }

    // ---------- Onglet 2 : Queue SCC (prod/cons borné) ----------

    static class QueueSccPanel extends JPanel {
        private final JTextArea log = newLog();
        private final JProgressBar bar = new JProgressBar();
        private final JLabel lblSize = new JLabel("—");
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService exec;
        private ScheduledExecutorService tick;
        private EventQueue<Integer> q;

        QueueSccPanel() {
            super(new BorderLayout(8, 8));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JTextField tfCap = new JTextField("5", 4);
            JTextField tfProd = new JTextField("2", 3);
            JTextField tfCons = new JTextField("2", 3);
            top.add(new JLabel("Cap:"));
            top.add(tfCap);
            top.add(new JLabel("Prod:"));
            top.add(tfProd);
            top.add(new JLabel("Cons:"));
            top.add(tfCons);

            JButton start = btn("Start", () -> {
                if (running.get())
                    return;
                try {
                    int cap = Math.max(1, Integer.parseInt(tfCap.getText().trim()));
                    int np = Math.max(1, Integer.parseInt(tfProd.getText().trim()));
                    int nc = Math.max(1, Integer.parseInt(tfCons.getText().trim()));

                    q = new EventQueueSCC<>(cap);
                    bar.setMaximum(cap);
                    running.set(true);
                    exec = Executors.newFixedThreadPool(np + nc);
                    log.setText("");
                    log(log, "[Start] EventQueueSCC cap=" + cap + " P=" + np + " C=" + nc);

                    // producteurs
                    for (int p = 0; p < np; p++) {
                        final int pid = p + 1;
                        exec.submit(() -> {
                            int i = 0;
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    q.put(i);
                                    log(log, "P" + pid + " -> put " + i);
                                    i++;
                                    Thread.sleep(60);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        });
                    }
                    // consommateurs
                    for (int c = 0; c < nc; c++) {
                        final int cid = c + 1;
                        exec.submit(() -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    Integer v = q.take();
                                    log(log, "C" + cid + " <- take " + v);
                                    Thread.sleep(120);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        });
                    }

                    // tick UI
                    tick = Executors.newSingleThreadScheduledExecutor();
                    tick.scheduleAtFixedRate(() -> {
                        int size = q.size();
                        SwingUtilities.invokeLater(() -> {
                            bar.setValue(size);
                            lblSize.setText("Taille: " + size + "/" + bar.getMaximum());
                        });
                    }, 0, 120, TimeUnit.MILLISECONDS);

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Valeurs invalides.");
                }
            });

            JButton stop = btn("Stop", this::stopAll);

            top.add(start);
            top.add(stop);
            add(top, BorderLayout.NORTH);

            JPanel mid = new JPanel(new BorderLayout());
            bar.setStringPainted(true);
            mid.add(titled(bar, "Occupation de la file"), BorderLayout.NORTH);
            mid.add(titled(new JScrollPane(log), "Journal"), BorderLayout.CENTER);
            mid.add(lblSize, BorderLayout.SOUTH);
            add(mid, BorderLayout.CENTER);
        }

        private void stopAll() {
            if (!running.getAndSet(false))
                return;
            if (exec != null)
                exec.shutdownNow();
            if (tick != null)
                tick.shutdownNow();
            log(log, "[Stop]");
        }
    }

    // ---------- Onglet 3 : Parking (limiteur de débit) ----------

    static class ParkingPanel extends JPanel {
        private final JTextArea log = newLog();
        private final JLabel lblConc = new JLabel("—");
        private final AtomicInteger concurrent = new AtomicInteger(0);
        private final AtomicInteger peak = new AtomicInteger(0);
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService exec;
        private Parking parking;

        ParkingPanel() {
            super(new BorderLayout(8, 8));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JTextField tfSlots = new JTextField("8", 4);
            JTextField tfTasks = new JTextField("50", 5);
            top.add(new JLabel("Slots:"));
            top.add(tfSlots);
            top.add(new JLabel("Tasks:"));
            top.add(tfTasks);

            JButton start = btn("Start", () -> {
                if (running.get())
                    return;
                try {
                    int slots = Math.max(1, Integer.parseInt(tfSlots.getText().trim()));
                    int tasks = Math.max(1, Integer.parseInt(tfTasks.getText().trim()));

                    parking = new Parking(slots);
                    concurrent.set(0);
                    peak.set(0);
                    running.set(true);
                    exec = Executors.newFixedThreadPool(Math.min(tasks, 32));
                    log.setText("");
                    log(log, "[Start] Parking slots=" + slots + " tasks=" + tasks);

                    for (int i = 0; i < tasks; i++) {
                        final int id = i + 1;
                        exec.submit(() -> {
                            try {
                                parking.enter();
                                int now = concurrent.incrementAndGet();
                                peak.accumulateAndGet(now, Math::max);
                                log(log, "Task " + id + " ENTER (conc=" + now + ")");
                                Thread.sleep(150);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                int now = concurrent.decrementAndGet();
                                parking.leave();
                                log(log, "Task " + id + " LEAVE (conc=" + now + ")");
                                SwingUtilities.invokeLater(() -> lblConc.setText("Concurrent max: " + peak.get()));
                            }
                        });
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Valeurs invalides.");
                }
            });

            JButton stop = btn("Stop", this::stopAll);

            top.add(start);
            top.add(stop);
            add(top, BorderLayout.NORTH);

            JPanel mid = new JPanel(new BorderLayout());
            mid.add(titled(new JScrollPane(log), "Journal"), BorderLayout.CENTER);
            mid.add(lblConc, BorderLayout.SOUTH);
            add(mid, BorderLayout.CENTER);
        }

        private void stopAll() {
            if (!running.getAndSet(false))
                return;
            if (exec != null)
                exec.shutdownNow();
            log(log, "[Stop]");
        }
    }

    // ---------- Onglet 4 : Lecteurs–Rédacteurs (RWFair) ----------

    static class RwFairPanel extends JPanel {
        private final JTextArea log = newLog();
        private final JLabel lblReaders = new JLabel("Lecteurs actifs: 0");
        private final AtomicInteger readers = new AtomicInteger(0);
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService exec;
        private RW rw;

        RwFairPanel() {
            super(new BorderLayout(8, 8));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JTextField tfReaders = new JTextField("6", 3);
            JTextField tfWriters = new JTextField("2", 3);
            top.add(new JLabel("Readers:"));
            top.add(tfReaders);
            top.add(new JLabel("Writers:"));
            top.add(tfWriters);

            JButton start = btn("Start", () -> {
                if (running.get())
                    return;
                try {
                    int nr = Math.max(1, Integer.parseInt(tfReaders.getText().trim()));
                    int nw = Math.max(1, Integer.parseInt(tfWriters.getText().trim()));
                    rw = new RWFair();
                    readers.set(0);
                    running.set(true);
                    exec = Executors.newFixedThreadPool(nr + nw);
                    log.setText("");
                    log(log, "[Start] RWFair R=" + nr + " W=" + nw);

                    for (int i = 0; i < nr; i++) {
                        final int id = i + 1;
                        exec.submit(() -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    rw.beginR();
                                    int now = readers.incrementAndGet();
                                    SwingUtilities.invokeLater(() -> lblReaders.setText("Lecteurs actifs: " + now));
                                    log(log, "R" + id + " -> lit");
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
                        });
                    }
                    for (int i = 0; i < nw; i++) {
                        final int id = i + 1;
                        exec.submit(() -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    rw.beginW();
                                    log(log, "W" + id + " -> écrit");
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
                        });
                    }

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Valeurs invalides.");
                }
            });

            JButton stop = btn("Stop", this::stopAll);

            top.add(start);
            top.add(stop);
            add(top, BorderLayout.NORTH);

            JPanel mid = new JPanel(new BorderLayout());
            mid.add(titled(new JScrollPane(log), "Journal"), BorderLayout.CENTER);
            mid.add(lblReaders, BorderLayout.SOUTH);
            add(mid, BorderLayout.CENTER);
        }

        private void stopAll() {
            if (!running.getAndSet(false))
                return;
            if (exec != null)
                exec.shutdownNow();
            log(log, "[Stop]");
        }
    }
}
