// nebula/ui/MainFrame.java
package nebula.ui;

import javax.swing.*;
import nebula.ui.alloc.AllocFifoPanel;
import nebula.ui.alloc.QueueSccPanel;
import nebula.ui.alloc.ParkingPanel;
import nebula.ui.alloc.RwFairPanel;

public class MainFrame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Nebula Hub — Démos Concurrence");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(1000, 700);

            var tabs = new JTabbedPane();
            tabs.addTab("Alloc FIFO", new AllocFifoPanel());
            tabs.addTab("Queue SCC", new QueueSccPanel());
            tabs.addTab("Parking", new ParkingPanel());
            tabs.addTab("R/W Fair", new RwFairPanel());

            f.setContentPane(tabs);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
