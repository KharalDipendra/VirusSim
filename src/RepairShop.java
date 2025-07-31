import java.awt.Color;
import java.awt.Graphics;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Repair Shop - shared resource that phones compete for
 *
 * Simple class but important for demonstrating resource contention.
 * Only one phone can be repaired at a time, so this creates a bottleneck
 * that phones have to coordinate around.
 */
public class RepairShop {
    public final int x;
    public final int y;
    private final AtomicBoolean busy = new AtomicBoolean(false); // Thread-safe flag

    public RepairShop(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public RepairShop() {
        this(380, 560); // Default position
    }

    // Draw the repair shop with status indication
    public void draw(Graphics g) {
        // Color coding: red = busy, green = available
        if (busy.get()) {
            g.setColor(Color.RED);
        } else {
            g.setColor(Color.GREEN);
        }

        g.fillRect(x, y, 40, 40);
        g.setColor(Color.WHITE);
        g.drawString("REPAIR", x + 2, y + 25);
        g.drawString(busy.get() ? "BUSY" : "FREE", x + 5, y + 55);
    }

    // Thread-safe state checking and updates
    public boolean isBusy() {
        return busy.get();
    }

    public void doingRepair() {
        busy.set(true);
    }

    public void free() {
        busy.set(false);
    }
}
