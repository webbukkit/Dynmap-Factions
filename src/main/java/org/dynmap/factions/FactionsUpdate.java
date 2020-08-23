package org.dynmap.factions;

import java.util.Date;

public class FactionsUpdate implements Runnable {
    private final DynmapFactionsPlugin kernel;
    private boolean runOnce;

    public FactionsUpdate(final DynmapFactionsPlugin kernel) {
        this.kernel = kernel;
    }

    public boolean isRunOnce() {
        return this.runOnce;
    }

    public void setRunOnce(boolean runOnce) {
        this.runOnce = runOnce;
    }

    @Override
    public synchronized void run() {
        System.out.println(new Date());
        if (!this.kernel.isStop()) {
            this.kernel.updateClaimedChunk();
            if (!this.isRunOnce()) {
                this.kernel.scheduleSyncDelayedTask(this, kernel.getUpdatePeriod());
            }
        }
    }
}
