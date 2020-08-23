package org.dynmap.factions;

import com.massivecraft.factions.Factions;
import com.massivecraft.factions.entity.BoardColl;
import com.massivecraft.factions.entity.Faction;
import com.massivecraft.factions.entity.FactionColl;
import com.massivecraft.factions.entity.Warp;
import com.massivecraft.massivecore.ps.PS;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.factions.area.AreaStyle;
import org.dynmap.factions.commons.Direction;
import org.dynmap.factions.players.PlayerSetUpdate;
import org.dynmap.factions.pojo.FactionBlock;
import org.dynmap.factions.pojo.FactionBlocks;
import org.dynmap.markers.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dynmap.factions.area.AreaCommon.*;
import static org.dynmap.factions.commons.Constant.*;
import static org.dynmap.factions.players.PlayerSetCommon.updatePlayerSets;

public class DynmapFactionsPlugin extends JavaPlugin {
    private static Logger log;

    private Map<String, AreaMarker> resareas = new HashMap<>();
    private Map<String, Marker> resmark = new HashMap<>();
    private boolean reload = false;
    private Plugin dynmap;
    private Plugin factions;
    private DynmapAPI dynmapAPI;
    private boolean displayFactionName;
    private boolean displayWarps;

    // Status of the plugin.
    private boolean stop;
    private MarkerSet set;
    private MarkerAPI markerAPI;
    private Factions factionAPI;
    private boolean playersets;
    private int blocksize;
    private FileConfiguration cfg;
    private long updatePeriod;
    private boolean updateEvent;
    private boolean use3d;
    private String infoWindow;
    private AreaStyle defstyle;
    private Map<String, AreaStyle> cusstyle;
    private Set<String> visible;
    private Set<String> hidden;

    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }

    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }

    public Plugin getDynmap() {
        return dynmap;
    }

    public void setDynmap(Plugin dynmap) {
        this.dynmap = dynmap;
    }

    public Plugin getFactions() {
        return factions;
    }

    public void setFactions(Plugin factions) {
        this.factions = factions;
    }

    public boolean isStop() {
        return this.stop;
    }

    public boolean isPlayersets() {
        return playersets;
    }

    public long getUpdatePeriod() {
        return updatePeriod;
    }

    public MarkerAPI getMarkerAPI() {
        return markerAPI;
    }

    @Override
    public void onLoad() {
        log = this.getLogger(); // Load the logger
    }

    @Override
    public void onEnable() {
        info("Initializing dynmap-faction3...");
        final PluginManager pm = getServer().getPluginManager();

        // Get Dynmap plugin
        dynmap = pm.getPlugin("dynmap");
        if (dynmap == null) {
            severe("Cannot find Dynmap! You have to download it on https://www.spigotmc.org/resources/dynmap.274/");
            return;
        }

        // Get Dynmap API
        dynmapAPI = (DynmapAPI) dynmap;

        // Get dynmap version
        String dynmapVersion = dynmapAPI.getDynmapVersion();
        info("Version of dynmap: " + dynmapVersion);

        // Get Factions
        factions = pm.getPlugin("Factions");
        if (factions == null) {
            severe("Cannot find Factions! You have to download it on https://www.spigotmc.org/resources/factions3-for-1-13.63602/");
            return;
        }

        // If both enabled, activate
        if (dynmap.isEnabled() && factions.isEnabled()) {
            activate();
        } else {
            severe("error, cannot active Dynmap-Faction3");
        }

        try {
            final MetricsLite metricsLite = new MetricsLite(this);
            metricsLite.start();
        } catch (final IOException iox) {
            severe(iox.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        resareas.clear();
        stop = true;
    }

    public int scheduleSyncDelayedTask(final Runnable run, final long period) {
        return getServer().getScheduler().scheduleSyncDelayedTask(this, run, period);
    }

    public int scheduleSyncDelayedTask(final Runnable run) {
        return getServer().getScheduler().scheduleSyncDelayedTask(this, run);
    }

    public void requestUpdatePlayerSet(final String factionUUID) {
        if (playersets) {
            scheduleSyncDelayedTask(new PlayerSetUpdate(this, factionUUID));
        }
    }

    public void requestUpdateFactions() {
        if (this.updateEvent) {
            final FactionsUpdate factionsUpdate = new FactionsUpdate(this);
            factionsUpdate.setRunOnce(true);
            scheduleSyncDelayedTask(factionsUpdate, TICK_RATE_RATIO);
        }
    }

    private boolean isVisible(final String id, final String worldName) {
        if (visible != null && visible.size() > 0) {
            if ((visible.contains(id) == false) && (visible.contains("world:" + worldName) == false)) {
                return false;
            }
        }
        if (hidden != null && hidden.size() > 0) {
            return !(hidden.contains(id) || hidden.contains("world:" + worldName));
        }
        return true;
    }

    /**
     * Handle specific faction on specific world
     */
    private void handleFactionOnWorld(String factionName, Faction fact, String world, LinkedList<FactionBlock> blocks, Map<String, AreaMarker> newmap, Map<String, Marker> newmark) {
        int poly_index = 0; /* Index of polygon for given faction */

        /* Build popup */
        final String desc = formatInfoWindow(infoWindow, fact);

        /* Handle areas */
        if (isVisible(factionName, world)) {
            if (blocks.isEmpty())
                return;
            LinkedList<FactionBlock> nodevals = new LinkedList<>();
            TileFlags curblks = new TileFlags();
            /* Loop through blocks: set flags on blockmaps */
            for (final FactionBlock b : blocks) {
                curblks.setFlag(b.getX(), b.getZ(), true); /* Set flag for block */
                nodevals.addLast(b);
            }
            /* Loop through until we don't find more areas */
            while (nodevals != null) {
                LinkedList<FactionBlock> ournodes = null;
                LinkedList<FactionBlock> newlist = null;
                TileFlags ourblks = null;
                int minx = Integer.MAX_VALUE;
                int minz = Integer.MAX_VALUE;
                for (FactionBlock node : nodevals) {
                    final int nodex = node.getX();
                    final int nodez = node.getZ();
                    /* If we need to start shape, and this block is not part of one yet */
                    if ((ourblks == null) && curblks.getFlag(nodex, nodez)) {
                        ourblks = new TileFlags();  /* Create map for shape */
                        ournodes = new LinkedList<>();
                        floodFillTarget(curblks, ourblks, nodex, nodez);   /* Copy shape */
                        ournodes.add(node); /* Add it to our node list */
                        minx = nodex;
                        minz = nodez;
                    }
                    /* If shape found, and we're in it, add to our node list */
                    else if ((ourblks != null) && ourblks.getFlag(nodex, nodez)) {
                        ournodes.add(node);
                        if (nodex < minx) {
                            minx = nodex;
                            minz = nodez;
                        } else if ((nodex == minx) && (nodez < minz)) {
                            minz = nodez;
                        }
                    } else {  /* Else, keep it in the list for the next polygon */
                        if (newlist == null) newlist = new LinkedList<>();
                        newlist.add(node);
                    }
                }
                nodevals = newlist; /* Replace list (null if no more to process) */
                if (ourblks != null) {
                    /* Trace outline of blocks - start from minx, minz going to x+ */
                    int init_x = minx;
                    int init_z = minz;
                    int cur_x = minx;
                    int cur_z = minz;
                    Direction dir = Direction.XPLUS;
                    ArrayList<int[]> linelist = new ArrayList<>();
                    linelist.add(new int[]{init_x, init_z}); // Add start point
                    while ((cur_x != init_x) || (cur_z != init_z) || (dir != Direction.ZMINUS)) {
                        switch (dir) {
                            case XPLUS: /* Segment in X+ Direction */
                                if (!ourblks.getFlag(cur_x + 1, cur_z)) { /* Right turn? */
                                    linelist.add(new int[]{cur_x + 1, cur_z}); /* Finish line */
                                    dir = Direction.ZPLUS;  /* Change Direction */
                                } else if (!ourblks.getFlag(cur_x + 1, cur_z - 1)) {  /* Straight? */
                                    cur_x++;
                                } else {  /* Left turn */
                                    linelist.add(new int[]{cur_x + 1, cur_z}); /* Finish line */
                                    dir = Direction.ZMINUS;
                                    cur_x++;
                                    cur_z--;
                                }
                                break;
                            case ZPLUS: /* Segment in Z+ Direction */
                                if (!ourblks.getFlag(cur_x, cur_z + 1)) { /* Right turn? */
                                    linelist.add(new int[]{cur_x + 1, cur_z + 1}); /* Finish line */
                                    dir = Direction.XMINUS;  /* Change Direction */
                                } else if (!ourblks.getFlag(cur_x + 1, cur_z + 1)) {  /* Straight? */
                                    cur_z++;
                                } else {  /* Left turn */
                                    linelist.add(new int[]{cur_x + 1, cur_z + 1}); /* Finish line */
                                    dir = Direction.XPLUS;
                                    cur_x++;
                                    cur_z++;
                                }
                                break;
                            case XMINUS: /* Segment in X- Direction */
                                if (!ourblks.getFlag(cur_x - 1, cur_z)) { /* Right turn? */
                                    linelist.add(new int[]{cur_x, cur_z + 1}); /* Finish line */
                                    dir = Direction.ZMINUS;  /* Change Direction */
                                } else if (!ourblks.getFlag(cur_x - 1, cur_z + 1)) {  /* Straight? */
                                    cur_x--;
                                } else {  /* Left turn */
                                    linelist.add(new int[]{cur_x, cur_z + 1}); /* Finish line */
                                    dir = Direction.ZPLUS;
                                    cur_x--;
                                    cur_z++;
                                }
                                break;
                            case ZMINUS: /* Segment in Z- Direction */
                                if (!ourblks.getFlag(cur_x, cur_z - 1)) { /* Right turn? */
                                    linelist.add(new int[]{cur_x, cur_z}); /* Finish line */
                                    dir = Direction.XPLUS;  /* Change Direction */
                                } else if (!ourblks.getFlag(cur_x - 1, cur_z - 1)) {  /* Straight? */
                                    cur_z--;
                                } else {  /* Left turn */
                                    linelist.add(new int[]{cur_x, cur_z}); /* Finish line */
                                    dir = Direction.XMINUS;
                                    cur_x--;
                                    cur_z--;
                                }
                                break;
                        }
                    }

                    /* Build information for specific area */
                    final String polyId = new StringBuilder().append(factionName).append("__").append(world).append("__").append(poly_index).toString();

                    final int sz = linelist.size();
                    final double[] x = new double[sz];
                    final double[] z = new double[sz];
                    for (int i = 0; i < sz; i++) {
                        final int[] line = linelist.get(i);
                        x[i] = (double) line[0] * (double) blocksize;
                        z[i] = (double) line[1] * (double) blocksize;
                    }

                    /* Find existing one */
                    AreaMarker areaMarker = resareas.remove(polyId); /* Existing area? */
                    if (areaMarker == null) {
                        areaMarker = set.createAreaMarker(polyId, factionName, false, world, x, z, false);
                        if (areaMarker == null) {
                            info("error adding area marker " + polyId);
                            return;
                        }
                    } else {
                        areaMarker.setCornerLocations(x, z); /* Replace corner locations */
                        areaMarker.setLabel(factionName);   /* Update label */
                    }
                    areaMarker.setDescription(desc); /* Set popup */

                    /* Set line and fill properties */
                    addStyle(cusstyle, defstyle, factionName, areaMarker);

                    /* Set the faction name */
                    if (displayFactionName) {
                        /* TODO: SHOW THE FACTION NAME */
                    }

                    /* Add to map */
                    newmap.put(polyId, areaMarker);
                    poly_index++;
                }
            }
        }
    }

    /* Update Factions information */
    public void updateClaimedChunk() {
        Map<String, AreaMarker> newmap = new HashMap<>(); /* Build new map */
        Map<String, Marker> newmark = new HashMap<>(); /* Build new map */

        /* Parse into faction centric mapping, split by world */
        Map<String, FactionBlocks> blocks_by_faction = new HashMap<>();

        FactionColl fc = FactionColl.get();
        Collection<Faction> facts = fc.getAll();
        for (Faction fact : facts) {
            Set<PS> chunks = BoardColl.get().getChunks(fact);
            String fid = fc.getUniverse() + "_" + fact.getId();
            FactionBlocks factblocks = blocks_by_faction.get(fid); /* Look up faction */
            if (factblocks == null) {    /* Create faction block if first time */
                factblocks = new FactionBlocks();
                blocks_by_faction.put(fid, factblocks);
            }

            for (final PS cc : chunks) {
                final String world = cc.getWorld();

                /* Get block set for given world */
                LinkedList<FactionBlock> blocks = factblocks.getBlocks().get(world);
                if (blocks == null) {
                    blocks = new LinkedList<>();
                    factblocks.getBlocks().put(world, blocks);
                }

                blocks.add(new FactionBlock(cc.getChunkX(), cc.getChunkZ())); /* Add to list */
            }
        }
        /* Loop through factions */
        for (final Faction faction : facts) {
            final String factname = ChatColor.stripColor(faction.getName());
            final String fid = new StringBuilder().append(fc.getUniverse()).append("_").append(faction.getId()).toString();
            final FactionBlocks factblocks = blocks_by_faction.get(fid); /* Look up faction */
            if (factblocks == null) continue;

            /* Loop through each world that faction has blocks on */
            for (Map.Entry<String, LinkedList<FactionBlock>> worldblocks : factblocks.getBlocks().entrySet()) {
                handleFactionOnWorld(factname, faction, worldblocks.getKey(), worldblocks.getValue(), newmap, newmark);
            }
            factblocks.clear();

            // Display warp on not
            if (displayWarps) {

                /* Now, add marker for warp location */
                for (Map.Entry<String, Warp> warpSet : faction.getWarps().entrySet()) {
                    final String oid = warpSet.getKey();
                    final Warp warp = warpSet.getValue();
                    final MarkerIcon ico = getMarkerIcon(cusstyle, defstyle, factname);
                    if (ico != null) {
                        final PS pos = warp.getLocation();
                        final Double lx = pos.getLocationX();
                        final Double ly = pos.getLocationY();
                        final Double lz = pos.getLocationZ();
                        final String labelName = new StringBuilder("[Warp] ").append(factname).append(" - ").append(warp.getName()).toString();

                        Marker marker = resmark.remove(oid);
                        if (marker == null) {
                            marker = set.createMarker(oid, labelName, warp.getWorld(), lx, ly, lz, ico, false);
                        } else {
                            marker.setLocation(warp.getWorld(), lx, ly, lz);
                            marker.setLabel(labelName);
                            marker.setMarkerIcon(ico);
                        }

                        if (marker != null) {
                            // Set popup
                            marker.setDescription(formatInfoWindow(infoWindow, faction));
                            newmark.put(oid, marker);
                        }
                    }
                }
            }
        }
        blocks_by_faction.clear();

        /* Now, review old map - anything left is gone */
        for (final AreaMarker oldm : resareas.values()) {
            oldm.deleteMarker();
        }

        for (final Marker oldm : resmark.values()) {
            oldm.deleteMarker();
        }
        /* And replace with new map */
        resareas = newmap;
        resmark = newmark;
    }

    public void activate() {
        info("Dynmap-Faction3 activated.");

        markerAPI = dynmapAPI.getMarkerAPI();
        if (markerAPI == null) {
            severe("Error loading Dynmap Marker API!");
            return;
        }

        // Connect to factions API
        factionAPI = Factions.get();

        blocksize = MAX_BLOCK_SIZE; /* Fixed at 16 */

        /* Load configuration */
        if (reload) {
            this.reloadConfig();
            if (set != null) {
                set.deleteMarkerSet();
                set = null;
            }
        } else {
            reload = true;
        }

        final FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */

        /* Now, add marker set for mobs (make it transient) */
        set = markerAPI.getMarkerSet("factions.markerset");
        if (set == null) {
            set = markerAPI.createMarkerSet("factions.markerset", cfg.getString("layer.name", "Factions"), null, false);
        } else {
            set.setMarkerSetLabel(cfg.getString("layer.name", "Factions"));
        }

        if (set == null) {
            severe("Error creating marker set");
            return;
        }
        /* Make sure these are empty (on reload) */
        resareas.clear();
        resmark.clear();

        final int minZoom = cfg.getInt("layer.minzoom", 0);
        if (minZoom > 0) {
            set.setMinZoom(minZoom);
        }

        set.setLayerPriority(cfg.getInt("layer.layerprio", 10));
        set.setHideByDefault(cfg.getBoolean("layer.hidebydefault", false));
        use3d = cfg.getBoolean("use3dregions", false);
        infoWindow = cfg.getString("infoWindow", DEF_INFO_WINDOW);
        displayFactionName = cfg.getBoolean("show-faction-name", true);
        displayWarps = cfg.getBoolean("display-warp", true);

        /* Get style information */
        defstyle = new AreaStyle(markerAPI, cfg, "regionstyle");
        cusstyle = new HashMap<>();

        final ConfigurationSection sect = cfg.getConfigurationSection("custstyle");
        if (sect != null) {
            Set<String> ids = sect.getKeys(false);

            for (final String id : ids) {
                cusstyle.put(id, new AreaStyle(markerAPI, cfg, "custstyle." + id, defstyle));
            }
        }
        List<String> vis = cfg.getStringList("visibleregions");
        if (vis != null) {
            visible = new HashSet<>(vis);
        }
        List<String> hid = cfg.getStringList("hiddenregions");
        if (hid != null) {
            hidden = new HashSet<>(hid);
        }
        /* Chec if player sets enabled */
        playersets = cfg.getBoolean("visibility-by-faction", false);
        if (playersets) {
            try {
                if (!dynmapAPI.testIfPlayerInfoProtected()) {
                    playersets = false;
                    info("Dynmap does not have player-info-protected enabled - visibility-by-faction will have no effect");
                }
            } catch (NoSuchMethodError x) {
                playersets = false;
                info("Dynmap does not support function needed for 'visibility-by-faction' - need to upgrade to 0.60 or later");
            }
        }

        updatePlayerSets(markerAPI, playersets);

        /* Set up update job - based on period */
        int per = cfg.getInt("update.period", 300); // 5 minutes
        if (per < MINIMAL_TIME_TO_UPDATE) {
            per = MINIMAL_TIME_TO_UPDATE;
        }

        // set update period
        this.updatePeriod = (per * TICK_RATE_RATIO);

        // set update when event
        this.updateEvent = cfg.getBoolean("update.event", true);

        // set stop to false
        this.stop = false;

        // First refresh
        scheduleSyncDelayedTask(this::updateClaimedChunk);

        // create the cron task
        scheduleSyncDelayedTask(new FactionsUpdate(this), updatePeriod);
        getServer().getPluginManager().registerEvents(new OurServerListener(this), this);

        info("version " + this.getDescription().getVersion() + " is activated");
    }
}
