package org.dynmap.factions.pojo;

public class FactionBlock {
    private final int x;
    private final int z;

    public FactionBlock(final int x, final int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }
}
