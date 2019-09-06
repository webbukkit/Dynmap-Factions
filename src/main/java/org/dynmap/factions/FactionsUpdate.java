package org.dynmap.factions;

public class FactionsUpdate implements Runnable {
    private final DynmapFactionsPlugin  kernel;
    private boolean runOnce;

    public FactionsUpdate(final DynmapFactionsPlugin kernel) {
        this.kernel = kernel;
    }

    public boolean isRunOnce() {
        return runOnce;
    }

    public void setRunOnce(boolean runOnce) {
        this.runOnce = runOnce;
    }

    @Override
    public synchronized void run() {
        if (!kernel.isStop()) {
            kernel.updateClaimedChunk();
            if (!isRunOnce()) {
                kernel.scheduleSyncDelayedTask(this, kernel.getUpdperiod());
            }
        }
    }
}
