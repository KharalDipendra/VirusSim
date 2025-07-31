import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Main simulation panel - this is where everything happens
 *
 * This got pretty complex, but it's a good showcase of threading patterns.
 * I'm using thread pools instead of creating threads manually, concurrent
 * collections to avoid synchronization headaches, and atomic variables for
 * counters that get updated from multiple threads.
 */
public class Panel extends JPanel implements KeyListener, ComponentListener {

    // Thread management - learned to use pools instead of raw threads
    private final ExecutorService phoneThreadPool;
    private final ScheduledExecutorService scheduledExecutor;
    private final AtomicBoolean isSimulationPaused = new AtomicBoolean(false);

    // Phone collection - ConcurrentLinkedQueue is thread-safe without locks
    private final ConcurrentLinkedQueue<Phone> phones = new ConcurrentLinkedQueue<>();
    private final RepairShop repairShop;

    // Images for different phone states - way better than colored circles
    private Image normalPhoneImg, infectedPhoneImg, repairingPhoneImg, repairedPhoneImg, repairShopImg;

    // Stats counters - atomic so multiple threads can update safely
    private final AtomicInteger totalPhones = new AtomicInteger(0);
    private final AtomicInteger infectedCount = new AtomicInteger(0);
    private final AtomicInteger repairedCount = new AtomicInteger(0);
    private final AtomicInteger repairingCount = new AtomicInteger(0);

    // For the infection timeline graph
    private final List<Integer> infectionHistory = new ArrayList<>();
    private final Object historyLock = new Object(); // Simple lock for the graph data

    // UI stuff
    private JButton pauseButton, addPhoneButton, infectRandomButton;
    private JLabel statsLabel;

    // Configuration - these values worked well through testing
    private static final int INFECTION_RANGE = 25;
    private static final int MAX_HISTORY_POINTS = 100;
    private static final int SIMULATION_AREA_WIDTH = 800;
    private static final int CONTROL_PANEL_WIDTH = 380;
    private static final int PHONE_SIZE = 35;    public Panel(JFrame frame) {
        this.repairShop = new RepairShop(SIMULATION_AREA_WIDTH / 2 - 20, 500);

        // Initialize thread pools
        this.phoneThreadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        this.scheduledExecutor = Executors.newScheduledThreadPool(3);

        setupUI();
        loadImages();
        initializeSimulation();
        startPeriodicTasks();
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setFocusable(true);
        addKeyListener(this);
        addComponentListener(this);

        // Request focus when mouse enters
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        // Create control panel
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.EAST);

        // Set preferred size for simulation area
        setPreferredSize(new Dimension(SIMULATION_AREA_WIDTH + CONTROL_PANEL_WIDTH, 600));
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.LIGHT_GRAY);
        panel.setPreferredSize(new Dimension(CONTROL_PANEL_WIDTH, 600));
        panel.setBorder(BorderFactory.createTitledBorder("Virus Simulation Control"));

        // Control buttons
        pauseButton = new JButton("Pause Simulation");
        pauseButton.addActionListener(e -> togglePause());

        addPhoneButton = new JButton("Add Phone (↑)");
        addPhoneButton.addActionListener(e -> addNewPhone());

        infectRandomButton = new JButton("Infect Random (V)");
        infectRandomButton.addActionListener(e -> infectRandomPhone());

        // Statistics label
        statsLabel = new JLabel("<html><h3>Statistics</h3></html>");

        // Add components with spacing
        panel.add(Box.createVerticalStrut(10));
        panel.add(pauseButton);
        panel.add(Box.createVerticalStrut(10));
        panel.add(addPhoneButton);
        panel.add(Box.createVerticalStrut(10));
        panel.add(infectRandomButton);
        panel.add(Box.createVerticalStrut(20));
        panel.add(statsLabel);
        panel.add(Box.createVerticalStrut(20));

        // Add graph panel
        JPanel graphPanel = new GraphPanel();
        graphPanel.setPreferredSize(new Dimension(350, 200));
        panel.add(graphPanel);

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private void loadImages() {
        try {
            String basePath = "Images/";
            normalPhoneImg = ImageIO.read(new File(basePath + "normal_not_infected.png"));
            infectedPhoneImg = ImageIO.read(new File(basePath + "phone_infected.png"));
            repairingPhoneImg = ImageIO.read(new File(basePath + "virus_going_to_repair.png"));
            repairedPhoneImg = ImageIO.read(new File(basePath + "virus_healed.png"));
            repairShopImg = ImageIO.read(new File(basePath + "repair_shop.png"));
        } catch (IOException e) {
            System.err.println("Error loading images: " + e.getMessage());
            // No big deal, we'll just draw colored circles instead
        }
    }

    private void initializeSimulation() {
        // Start with one phone so there's something to see
        addNewPhone();

        // 60 FPS rendering - smooth but not crazy CPU usage
        new Timer(16, e -> repaint()).start();
    }

    private void startPeriodicTasks() {
        // Background thread checks for virus spread every 50ms
        scheduledExecutor.scheduleAtFixedRate(this::calculateVirusSpread, 0, 50, TimeUnit.MILLISECONDS);

        // Update stats display every 100ms - frequent enough to feel responsive
        scheduledExecutor.scheduleAtFixedRate(this::updateStatistics, 0, 100, TimeUnit.MILLISECONDS);

        // Collect graph data every 500ms - don't need crazy resolution
        scheduledExecutor.scheduleAtFixedRate(this::collectGraphData, 0, 500, TimeUnit.MILLISECONDS);
    }    private void calculateVirusSpread() {
        if (isSimulationPaused.get()) return;

        List<Phone> phoneList = new ArrayList<>(phones);

        // Check for infections between phones
        for (int i = 0; i < phoneList.size(); i++) {
            Phone p1 = phoneList.get(i);
            if (!p1.isInfected() || p1.isRepaired()) continue;

            for (int j = i + 1; j < phoneList.size(); j++) {
                Phone p2 = phoneList.get(j);
                if (p2.isInfected() || p2.isRepaired()) continue;

                double distance = Math.sqrt(
                    Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2)
                );

                if (distance < INFECTION_RANGE) {
                    p2.infect();
                }
            }
        }
    }

    private void updateStatistics() {
        SwingUtilities.invokeLater(() -> {
            int total = 0, infected = 0, repairing = 0, repaired = 0;

            for (Phone phone : phones) {
                total++;
                if (phone.isInfected() && !phone.isRepaired()) {
                    if (phone.isGoingToRepair()) {
                        repairing++;
                    } else {
                        infected++;
                    }
                } else if (phone.isRepaired()) {
                    repaired++;
                }
            }

            totalPhones.set(total);
            infectedCount.set(infected);
            repairingCount.set(repairing);
            repairedCount.set(repaired);

            updateStatsDisplay();
        });
    }

    private void updateStatsDisplay() {
        if (statsLabel != null) {
            statsLabel.setText(String.format(
                "<html><h3>Statistics</h3>" +
                "Total Phones: %d<br>" +
                "Healthy: %d<br>" +
                "Infected: %d<br>" +
                "Seeking Repair: %d<br>" +
                "Repaired: %d<br>" +
                "Infection Rate: %.1f%%<br>" +
                "<br><b>Multithreading Info:</b><br>" +
                "Active Threads: %d<br>" +
                "Thread Pool: Cached<br>" +
                "Sync Method: Atomic + ConcurrentQueue" +
                "</html>",
                totalPhones.get(),
                totalPhones.get() - infectedCount.get() - repairingCount.get() - repairedCount.get(),
                infectedCount.get(),
                repairingCount.get(),
                repairedCount.get(),
                totalPhones.get() > 0 ? (infectedCount.get() + repairingCount.get()) * 100.0 / totalPhones.get() : 0.0,
                Thread.activeCount()
            ));
        }
    }

    private void collectGraphData() {
        synchronized (historyLock) {
            infectionHistory.add(infectedCount.get() + repairingCount.get());
            if (infectionHistory.size() > MAX_HISTORY_POINTS) {
                infectionHistory.remove(0);
            }
        }
    }

    private void togglePause() {
        boolean wasPaused = isSimulationPaused.getAndSet(!isSimulationPaused.get());

        if (wasPaused) {
            // Resume all phones
            for (Phone phone : phones) {
                phone.resume();
            }
            pauseButton.setText("Pause Simulation");
        } else {
            // Pause all phones
            for (Phone phone : phones) {
                phone.pause();
            }
            pauseButton.setText("Resume Simulation");
        }
    }

    private void addNewPhone() {
        Phone newPhone = new Phone(repairShop);
        newPhone.setRange(SIMULATION_AREA_WIDTH - 40, getHeight() - 40);
        phones.add(newPhone);
        phoneThreadPool.execute(newPhone);
    }

    private void infectRandomPhone() {
        List<Phone> healthyPhones = new ArrayList<>();
        for (Phone phone : phones) {
            if (!phone.isInfected() && !phone.isRepaired()) {
                healthyPhones.add(phone);
            }
        }

        if (!healthyPhones.isEmpty()) {
            Phone randomPhone = healthyPhones.get((int) (Math.random() * healthyPhones.size()));
            randomPhone.infect();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        // Enable antialiasing for smoother graphics
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw simulation area border
        g2d.setColor(Color.BLACK);
        g2d.drawRect(0, 0, SIMULATION_AREA_WIDTH, getHeight());

        // Draw repair shop
        drawRepairShop(g2d);

        // Draw all phones
        drawPhones(g2d);

        // Draw infection range visualization for infected phones
        drawInfectionRanges(g2d);

        g2d.dispose();
    }

    private void drawRepairShop(Graphics2D g2d) {
        if (repairShopImg != null) {
            g2d.drawImage(repairShopImg, repairShop.x, repairShop.y, 40, 40, this);
        } else {
            // Fallback drawing
            g2d.setColor(Color.BLUE);
            g2d.fillRect(repairShop.x, repairShop.y, 40, 40);
            g2d.setColor(Color.WHITE);
            g2d.drawString("REPAIR", repairShop.x + 2, repairShop.y + 25);
        }
    }

    private void drawPhones(Graphics2D g2d) {
        // Remove phones with 0 health
        phones.removeIf(phone -> phone.getHealth() <= 0);

        for (Phone phone : phones) {
            Image phoneImage = getPhoneImage(phone);

            if (phoneImage != null) {
                g2d.drawImage(phoneImage, phone.x, phone.y, PHONE_SIZE, PHONE_SIZE, this);
            } else {
                // Fallback to colored circles
                g2d.setColor(getPhoneColor(phone));
                g2d.fillOval(phone.x, phone.y, PHONE_SIZE, PHONE_SIZE);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(phone.x, phone.y, PHONE_SIZE, PHONE_SIZE);
            }

            // Draw health bar for infected phones
            if (phone.isInfected() && !phone.isRepaired()) {
                drawHealthBar(g2d, phone);
            }
        }
    }

    private Image getPhoneImage(Phone phone) {
        if (phone.isRepaired()) return repairedPhoneImg;
        if (phone.isGoingToRepair()) return repairingPhoneImg;
        if (phone.isInfected()) return infectedPhoneImg;
        return normalPhoneImg;
    }

    private Color getPhoneColor(Phone phone) {
        if (phone.isRepaired()) return Color.GREEN;
        if (phone.isGoingToRepair()) return Color.ORANGE;
        if (phone.isInfected()) return Color.RED;
        return Color.BLUE;
    }

    private void drawHealthBar(Graphics2D g2d, Phone phone) {
        int barWidth = PHONE_SIZE;
        int barHeight = 4;
        int healthPercent = Math.max(0, phone.getHealth() * 100 / 500);

        // Background
        g2d.setColor(Color.BLACK);
        g2d.fillRect(phone.x, phone.y - 8, barWidth, barHeight);

        // Health bar
        g2d.setColor(healthPercent > 60 ? Color.GREEN : healthPercent > 30 ? Color.YELLOW : Color.RED);
        g2d.fillRect(phone.x, phone.y - 8, (barWidth * healthPercent) / 100, barHeight);
    }    private void drawInfectionRanges(Graphics2D g2d) {
        g2d.setColor(new Color(255, 0, 0, 30)); // Semi-transparent red
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                      10.0f, new float[]{5.0f}, 0.0f)); // Dashed line

        for (Phone phone : phones) {
            if (phone.isInfected() && !phone.isGoingToRepair() && !phone.isRepaired()) {
                g2d.drawOval(phone.x - INFECTION_RANGE + 10, phone.y - INFECTION_RANGE + 10,
                           INFECTION_RANGE * 2, INFECTION_RANGE * 2);
            }
        }
    }

    // Inner class for the graph panel
    private class GraphPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            setBackground(Color.WHITE);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

            synchronized (historyLock) {
                if (infectionHistory.size() < 2) return;

                g2d.setColor(Color.RED);
                g2d.setStroke(new BasicStroke(2.0f));

                int maxInfected = Math.max(1, infectionHistory.stream().mapToInt(Integer::intValue).max().orElse(1));

                for (int i = 1; i < infectionHistory.size(); i++) {
                    int x1 = (i - 1) * getWidth() / MAX_HISTORY_POINTS;
                    int y1 = getHeight() - (infectionHistory.get(i - 1) * getHeight() / maxInfected);
                    int x2 = i * getWidth() / MAX_HISTORY_POINTS;
                    int y2 = getHeight() - (infectionHistory.get(i) * getHeight() / maxInfected);

                    g2d.drawLine(x1, y1, x2, y2);
                }
            }

            // Draw title
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString("Infection Timeline", 10, 15);

            g2d.dispose();
        }
    }

    // Key event handling with improved focus management
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> addNewPhone();
            case KeyEvent.VK_V -> infectRandomPhone();
            case KeyEvent.VK_SPACE -> togglePause();
        }
    }

    @Override
    public void componentResized(ComponentEvent e) {
        for (Phone phone : phones) {
            phone.setRange(SIMULATION_AREA_WIDTH - 40, getHeight() - 40);
        }
    }

    // Cleanup method
    public void cleanup() {
        phoneThreadPool.shutdown();
        scheduledExecutor.shutdown();
        try {
            if (!phoneThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                phoneThreadPool.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            phoneThreadPool.shutdownNow();
            scheduledExecutor.shutdownNow();
        }
    }

    // Unused interface methods
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void componentMoved(ComponentEvent e) {}
    @Override public void componentShown(ComponentEvent e) {}
    @Override public void componentHidden(ComponentEvent e) {}
}
