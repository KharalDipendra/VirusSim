import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phone class - each one runs in its own thread
 *
 * This is where most of the threading magic happens. Each phone bounces around
 * independently, gets infected, seeks repair, etc. I'm using atomic variables
 * everywhere to avoid synchronized blocks - cleaner and faster.
 */
public class Phone implements Runnable {
    // Where the phone is and how fast it's moving
    public volatile int x = 0;
    public volatile int y = 0;
    private volatile int vx = 0;
    private volatile int vy = 0;

    // Thread control stuff - atomic because multiple threads might check these
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final Object pauseLock = new Object(); // For the wait/notify pattern

    // Tweakable constants - found these values through trial and error
    private static final int DELAY_MS = 8; // 8ms gives smooth movement without hogging CPU
    private static final int INFECTION_HEALTH_THRESHOLD = 300; // When phones panic and seek repair
    private static final int REPAIR_SHOP_SPEED = 7; // Faster movement toward repair
    private static final int MOVEMENT_SPEED = 1; // Normal bouncing speed

    // Screen boundaries - set by Panel when window resizes
    private volatile int width = 800;
    private volatile int height = 600;

    // Phone state - atomic so we can change them safely from any thread
    private final AtomicInteger health = new AtomicInteger(500);
    private final AtomicBoolean isInfected = new AtomicBoolean(false);
    private final AtomicBoolean goingToRepair = new AtomicBoolean(false);
    private final AtomicBoolean isRepaired = new AtomicBoolean(false);

    // Shared stuff
    private final RepairShop repairShop;
    private final Random random = new Random();

    // Each phone gets a unique ID for debugging
    private final int phoneId;
    private static final AtomicInteger phoneIdCounter = new AtomicInteger(0);

    public Phone(RepairShop repairShop) {
        this.repairShop = repairShop;
        this.phoneId = phoneIdCounter.incrementAndGet();

        // Start somewhere random on screen
        initializePosition();
        initializeVelocity();
    }

    private void initializePosition() {
        // Make sure phones don't spawn too close to edges
        x = random.nextInt(Math.max(1, width - 50));
        y = random.nextInt(Math.max(1, height - 50));
    }

    private void initializeVelocity() {
        // Random direction, but consistent speed
        vx = random.nextBoolean() ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        vy = random.nextBoolean() ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
    }

    public void setRange(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // Getters that other threads can safely call
    public boolean isInfected() { return isInfected.get(); }
    public boolean isGoingToRepair() { return goingToRepair.get(); }
    public boolean isRepaired() { return isRepaired.get(); }
    public int getHealth() { return health.get(); }
    public int getPhoneId() { return phoneId; }

    // Thread control - used by the Panel to pause/resume everything
    public void pause() {
        isPaused.set(true);
    }

    public void resume() {
        synchronized (pauseLock) {
            isPaused.set(false);
            pauseLock.notifyAll(); // Wake up any waiting threads
        }
    }

    public void stop() {
        isRunning.set(false);
        resume(); // Make sure thread isn't stuck waiting
    }

    // Block execution when paused - classic wait/notify pattern
    private void checkPauseState() throws InterruptedException {
        while (isPaused.get()) {
            synchronized (pauseLock) {
                if (isPaused.get()) {
                    pauseLock.wait();
                }
            }
        }
    }

    // Infect this phone - using compareAndSet to avoid double infections
    public void infect() {
        if (!isInfected.compareAndSet(false, true)) {
            return; // Already infected, bail out
        }
        health.set(500); // Reset health when infected
    }

    // Fix the phone and make it immune
    public void repaired() {
        isInfected.set(false);
        goingToRepair.set(false);
        isRepaired.set(true);
        health.set(500);
    }

    // Main thread loop - this is what runs when you call new Thread(phone).start()
    @Override
    public void run() {
        Thread.currentThread().setName("Phone-" + phoneId); // Helpful for debugging

        try {
            while (isRunning.get() && health.get() > 0) {
                checkPauseState(); // Will block here if simulation is paused

                if (!isRunning.get()) break; // Double check we should keep going

                updateInfectionState();
                updateMovement();

                Thread.sleep(DELAY_MS); // Small delay so we don't eat all the CPU
            }
        } catch (InterruptedException e) {
            // Clean shutdown
            Thread.currentThread().interrupt();
        }
    }

    // Handle infection progression and repair seeking
    private void updateInfectionState() {
        if (!isInfected.get()) return;

        int currentHealth = health.decrementAndGet(); // Lose health each frame

        // When health gets low, try to get to the repair shop
        if (currentHealth <= INFECTION_HEALTH_THRESHOLD &&
            !goingToRepair.get() && !isRepaired.get()) {

            // Only one phone can use the repair shop at a time
            synchronized (repairShop) {
                if (!repairShop.isBusy() && goingToRepair.compareAndSet(false, true)) {
                    repairShop.doingRepair();
                }
            }
        }
    }

    // Decide how to move based on current state
    private void updateMovement() {
        if (goingToRepair.get()) {
            // Beeline for the repair shop
            if (!moveToRepairShop()) {
                enterRepairShop(); // We made it!
            }
        } else {
            // Normal bouncing around
            move();
        }
    }

    // Regular bouncing movement with wall collision
    private void move() {
        x += vx;
        y += vy;

        // Bounce off walls - the 35 accounts for phone size
        if (x <= 0 || x >= width - 35) {
            vx *= -1;
            x = Math.max(0, Math.min(width - 35, x)); // Keep it in bounds
        }
        if (y <= 0 || y >= height - 35) {
            vy *= -1;
            y = Math.max(0, Math.min(height - 35, y));
        }
    }

    // Move toward the repair shop in a straight line
    private boolean moveToRepairShop() {
        int targetX = repairShop.x + 10; // Center of repair shop
        int targetY = repairShop.y + 10;

        int dx = targetX - x;
        int dy = targetY - y;

        // Close enough? Consider us there
        if (Math.abs(dx) <= 5 && Math.abs(dy) <= 5) {
            return false;
        }

        // Move toward target, but not faster than REPAIR_SHOP_SPEED
        if (dx != 0) {
            x += dx > 0 ? Math.min(REPAIR_SHOP_SPEED, dx) : Math.max(-REPAIR_SHOP_SPEED, dx);
        }
        if (dy != 0) {
            y += dy > 0 ? Math.min(REPAIR_SHOP_SPEED, dy) : Math.max(-REPAIR_SHOP_SPEED, dy);
        }

        return true; // Still moving
    }

    // Simulate getting repaired - takes some time
    private void enterRepairShop() {
        try {
            // Random repair time makes it more interesting
            Thread.sleep(1000 + random.nextInt(500));
            repaired(); // All better!
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Always free up the repair shop, even if interrupted
            repairShop.free();
            goingToRepair.set(false);
        }
    }
}
