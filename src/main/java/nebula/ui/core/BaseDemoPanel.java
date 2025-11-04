package nebula.ui.core;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.*;

public abstract class BaseDemoPanel extends JPanel implements DemoView {
    protected volatile ExecutorService exec;
    protected volatile ScheduledExecutorService tick;

    protected final JTextArea log = new JTextArea();
    protected final JEditorPane explain = new JEditorPane("text/html", "<i>—</i>");

    protected final JButton btnStart = new JButton("Start");
    protected final JButton btnStop = new JButton("Stop");
    protected final JButton btnReset = new JButton("Reset");

    protected final JToggleButton tglStep = new JToggleButton("Step-by-step");
    protected final JToggleButton tglFull = new JToggleButton("Full run", true);
    protected final ButtonGroup grpMode = new ButtonGroup();

    protected final JButton btnPrev = new JButton("Prev");
    protected final JButton btnDo = new JButton("Do step");
    protected final JButton btnNext = new JButton("Next");
    protected final JToggleButton tglAuto = new JToggleButton("Auto");
    protected final JSpinner spdMs = new JSpinner(new SpinnerNumberModel(600, 100, 5000, 100));

    protected DemoMode mode = DemoMode.FULL_RUN;
    protected StepRunner stepper = new StepRunner(List.of());
    private boolean autoRunning = false;

    // <<< NOUVEAU : exposés aux sous-classes (pas d'appel virtuel pendant super)
    private final JPanel extraNorth = new JPanel(new GridLayout(0, 1, 0, 4));
    private final JPanel controlsBar = new JPanel(new FlowLayout(FlowLayout.LEFT));

    protected BaseDemoPanel(String title) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder(title));
        ensureExecs();

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        explain.setEditable(false);
        explain.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        // Barre Mode
        grpMode.add(tglFull);
        grpMode.add(tglStep);
        var modeBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeBar.add(new JLabel("Mode:"));
        modeBar.add(tglFull);
        modeBar.add(tglStep);

        // Barre Controls de base
        controlsBar.add(btnStart);
        controlsBar.add(btnStop);
        controlsBar.add(btnReset);

        // Barre Step
        var stepBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stepBar.add(new JLabel("Step:"));
        stepBar.add(btnPrev);
        stepBar.add(btnDo);
        stepBar.add(btnNext);
        stepBar.add(new JLabel("Auto(ms):"));
        stepBar.add(spdMs);
        stepBar.add(tglAuto);

        // Zone nord empilée
        var north = new JPanel(new GridLayout(0, 1, 0, 4));
        north.add(modeBar);
        north.add(controlsBar);
        north.add(stepBar);
        north.add(extraNorth);
        add(north, BorderLayout.NORTH);

        // Centre : log + explications
        var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(log), new JScrollPane(explain));
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        // Actions
        tglFull.addActionListener(e -> switchMode(DemoMode.FULL_RUN));
        tglStep.addActionListener(e -> switchMode(DemoMode.STEP_BY_STEP));

        btnStart.addActionListener(e -> {
            if (mode == DemoMode.FULL_RUN)
                startDemo();
            else
                showStep(0);
        });
        btnStop.addActionListener(e -> stopDemo());
        btnReset.addActionListener(e -> resetDemo());

        btnPrev.addActionListener(e -> stepPrev());
        btnDo.addActionListener(e -> stepDo());
        btnNext.addActionListener(e -> stepNext());

        tglAuto.addActionListener(e -> {
            autoRunning = tglAuto.isSelected();
            if (autoRunning)
                autoRun();
        });

        updateStepperEnabled();
    }

    // --- Accès pour les sous-classes (utiliser APRÈS super()) ---
    protected JPanel extraNorth() {
        return extraNorth;
    }

    protected JPanel controlsPanel() {
        return controlsBar;
    }

    // --- (le reste identique à ta version précédente) ---
    protected void ensureExecs() {
        if (exec == null || exec.isShutdown() || exec.isTerminated())
            exec = Executors.newCachedThreadPool();
        if (tick == null || tick.isShutdown() || tick.isTerminated())
            tick = Executors.newSingleThreadScheduledExecutor();
    }

    protected void switchMode(DemoMode m) {
        mode = m;
        updateStepperEnabled();
        explain.setText(mode == DemoMode.FULL_RUN
                ? "<i>Mode complet : exécute toute la démo.</i>"
                : "<i>Mode pas-à-pas : utilisez Prev / Do / Next.</i>");
    }

    protected void updateStepperEnabled() {
        boolean step = (mode == DemoMode.STEP_BY_STEP);
        btnPrev.setEnabled(step);
        btnDo.setEnabled(step);
        btnNext.setEnabled(step);
        tglAuto.setEnabled(step);
        spdMs.setEnabled(step);
        btnStart.setEnabled(!step);
    }

    protected void setSteps(List<Step> steps) {
        this.stepper = new StepRunner(steps);
        showStep(0);
    }

    protected void showStep(int idx) {
        if (stepper.size() == 0)
            return;
        // amène l’index voulu sans perform()
        while (stepper.index() > idx) {
            stepper.prev();
        }
        while (stepper.index() < idx) {
            stepper.next();
        }
        var s = stepper.current();
        explain.setText("<html><b>" + s.title() + "</b><br/>" + s.descriptionHtml() + "</html>");
        logln("[Step] " + s.id() + " — " + s.title());
    }

    protected void stepPrev() {
        if (stepper.prev()) {
            var s = stepper.current();
            explain.setText("<html><b>" + s.title() + "</b><br/>" + s.descriptionHtml() + "</html>");
            logln("[Step] " + s.id());
        }
    }

    protected void stepDo() {
        try {
            ensureExecs(); // recrée exec/tick si besoin
            stepper.performCurrent(); // exécute UNE FOIS l’étape courante
        } catch (Exception ex) {
            logln("[ERR] " + ex);
        }
    }

    protected void stepNext() {
        if (stepper.next()) {
            var s = stepper.current();
            explain.setText("<html><b>" + s.title() + "</b><br/>" + s.descriptionHtml() + "</html>");
            logln("[Step] " + s.id());
        }
    }

    private void autoRun() {
        ensureExecs();
        exec.submit(() -> {
            while (autoRunning) {
                try {
                    SwingUtilities.invokeAndWait(this::stepDo); // exécute l’étape courante
                    Thread.sleep((Integer) spdMs.getValue());
                    if (!stepper.hasNext())
                        break; // dernière étape : on sort
                    SwingUtilities.invokeAndWait(this::stepNext); // NAVIGUE seulement
                    Thread.sleep((Integer) spdMs.getValue());
                } catch (Exception e) {
                    logln("[ERR auto] " + e);
                    break;
                }
            }
            SwingUtilities.invokeLater(() -> tglAuto.setSelected(false));
            autoRunning = false;
        });
    }

    protected void ui(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    protected void logln(String s) {
        ui(() -> {
            log.append(s + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    protected void shutdownExecs() {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
        if (tick != null) {
            tick.shutdownNow();
            tick = null;
        }
    }
}
