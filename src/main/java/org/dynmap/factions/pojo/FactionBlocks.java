package org.dynmap.factions.pojo;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class FactionBlocks {
    private final Map<String, LinkedList<FactionBlock>> blocks = new HashMap<>();

    public Map<String, LinkedList<FactionBlock>> getBlocks() {
        return blocks;
    }

    public void clear() {
        blocks.clear();
    }
}
