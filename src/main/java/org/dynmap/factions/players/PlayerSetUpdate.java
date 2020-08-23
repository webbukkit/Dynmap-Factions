package org.dynmap.factions.players;

import org.dynmap.factions.DynmapFactionsPlugin;

import static org.dynmap.factions.players.PlayerSetCommon.updatePlayerSet;

public class PlayerSetUpdate implements Runnable {
    private final DynmapFactionsPlugin kernel;
    private final String factionUUID;

    public PlayerSetUpdate(final DynmapFactionsPlugin kernel, final String factionUUID) {
        this.kernel = kernel;
        this.factionUUID = factionUUID;
    }

    @Override
    public void run() {
        if (!kernel.isStop()) {
            updatePlayerSet(kernel.getMarkerAPI(), factionUUID);
        }
    }
}
