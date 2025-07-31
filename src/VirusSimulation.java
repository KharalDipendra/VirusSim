import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Main class for the virus simulation
 *
 * Simple entry point that sets up the GUI. Nothing fancy here,
 * just creates the window and lets Panel do all the heavy lifting.
 *
 * @author Dipendra Kharal
 */
public class VirusSimulation {

    public static void main(String[] args) {
        // Swing stuff needs to run on the EDT, learned this the hard way
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Mobile Phone Virus Simulation");
            frame.setSize(1200, 800);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false); // Keeps the layout sane

            Panel panel = new Panel(frame);
            frame.getContentPane().add(panel);
            frame.setVisible(true);

            // Center it on screen - looks more professional
            frame.setLocationRelativeTo(null);
        });
    }
}
