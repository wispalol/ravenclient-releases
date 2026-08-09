package org.ravenclient.overlay;

/**
 * Singleton that holds live game data scraped from the Minecraft process output.
 * Values are updated by OverlayManager's log parser thread.
 */
public class GameDataBridge {

    private static final GameDataBridge INSTANCE = new GameDataBridge();
    public static GameDataBridge getInstance() { return INSTANCE; }

    private volatile String coords = "X: 0  Y: 64  Z: 0";
    private volatile String direction = "Facing: North";
    private volatile String server = "Singleplayer";
    private volatile String speed = "0.0 b/s";
    private volatile int ping = 0;
    private volatile int leftCps = 0;
    private volatile int rightCps = 0;
    private volatile int combo = 0;
    private volatile int hits = 0;
    private volatile int kills = 0;
    private volatile int deaths = 0;
    private volatile TargetInfo target = null;
    private volatile int fps = 0;
    private volatile java.util.List<String> potions = new java.util.ArrayList<>();
    private volatile double armorPercent = 0;
    private volatile int durability = 0;
    private volatile int maxDurability = 0;
    private volatile String heldItem = "None";
    private volatile String scoreboard = "";

    // Click tracking
    private final long[] leftClicks = new long[20];
    private final long[] rightClicks = new long[20];
    private int leftIdx = 0, rightIdx = 0;

    public String getCoords() { return coords; }
    public String getDirection() { return direction; }
    public String getServer() { return server; }
    public String getSpeed() { return speed; }
    public int getPing() { return ping; }
    public int getLeftCps() { return leftCps; }
    public int getRightCps() { return rightCps; }
    public int getCombo() { return combo; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public double getKdr() { return deaths == 0 ? kills : (double) kills / deaths; }
    public TargetInfo getTarget() { return target; }
    public int getFps() { return fps; }
    public java.util.List<String> getPotions() { return potions; }
    public double getArmorPercent() { return armorPercent; }
    public int getDurability() { return durability; }
    public int getMaxDurability() { return maxDurability; }
    public String getHeldItem() { return heldItem; }
    public String getScoreboard() { return scoreboard; }
    public int getHits() { return hits; }

    public void setCoords(double x, double y, double z) {
        coords = String.format("X: %.0f  Y: %.0f  Z: %.0f", x, y, z);
    }
    public void setDirection(String dir) { this.direction = "Facing: " + dir; }
    public void setServer(String s) { this.server = s; }
    public void setSpeed(double s) { this.speed = String.format("%.1f b/s", s); }
    public void setPing(int p) { this.ping = p; }
    public void setTarget(TargetInfo t) { this.target = t; }
    public void clearTarget() { this.target = null; }
    public void incrementKills() { kills++; }
    public void incrementDeaths() { deaths++; }
    public void setCombo(int c) { this.combo = c; }
    public void setFps(int f) { this.fps = f; }
    public void setPotions(java.util.List<String> p) { this.potions = p; }
    public void setArmorPercent(double a) { this.armorPercent = a; }
    public void setDurability(int d, int max) { this.durability = d; this.maxDurability = max; }
    public void setHeldItem(String item) { this.heldItem = item; }
    public void setScoreboard(String sb) { this.scoreboard = sb; }
    public void incrementHits() { hits++; }

    public void recordLeftClick() {
        leftClicks[leftIdx++ % leftClicks.length] = System.currentTimeMillis();
        leftCps = countRecent(leftClicks);
    }

    public void recordRightClick() {
        rightClicks[rightIdx++ % rightClicks.length] = System.currentTimeMillis();
        rightCps = countRecent(rightClicks);
    }

    private int countRecent(long[] times) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long t : times) if (now - t <= 1000) count++;
        return count;
    }

    public record TargetInfo(String name, int hp, int maxHp) {}
}
