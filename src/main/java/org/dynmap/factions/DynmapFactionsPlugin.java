package org.dynmap.factions;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PlayerSet;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.event.FPlayerJoinEvent;
import com.massivecraft.factions.event.FPlayerLeaveEvent;
import com.massivecraft.factions.event.FactionCreateEvent;
import com.massivecraft.factions.event.FactionDisbandEvent;
import com.massivecraft.factions.event.FactionRenameEvent;
import com.massivecraft.factions.event.LandClaimEvent;
import com.massivecraft.factions.event.LandUnclaimAllEvent;
import com.massivecraft.factions.event.LandUnclaimEvent;
import com.massivecraft.factions.struct.FFlag;
import com.massivecraft.factions.struct.TerritoryAccess;

public class DynmapFactionsPlugin extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String LOG_PREFIX = "[Dynmap-Factions] ";
    private static final String DEF_INFOWINDOW = "<div class=\"infowindow\"><span style=\"font-size:120%;\">%regionname%</span><br />Flags<br /><span style=\"font-weight:bold;\">%flags%</span></div>";
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    Plugin factions;
    Factions factapi;
    boolean playersets;
    
    int blocksize;
    
    FileConfiguration cfg;
    MarkerSet set;
    long updperiod;
    boolean use3d;
    String infowindow;
    AreaStyle defstyle;
    Map<String, AreaStyle> cusstyle;
    Set<String> visible;
    Set<String> hidden;
    boolean stop;
    
    private class AreaStyle {
        String strokecolor;
        double strokeopacity;
        int strokeweight;
        String fillcolor;
        double fillopacity;
        String homemarker;
        MarkerIcon homeicon;

        AreaStyle(FileConfiguration cfg, String path, AreaStyle def) {
            strokecolor = cfg.getString(path+".strokeColor", def.strokecolor);
            strokeopacity = cfg.getDouble(path+".strokeOpacity", def.strokeopacity);
            strokeweight = cfg.getInt(path+".strokeWeight", def.strokeweight);
            fillcolor = cfg.getString(path+".fillColor", def.fillcolor);
            fillopacity = cfg.getDouble(path+".fillOpacity", def.fillopacity);
            homemarker = cfg.getString(path+".homeicon", def.homemarker);
            if(homemarker != null) {
                homeicon = markerapi.getMarkerIcon(homemarker);
                if(homeicon == null) {
                    severe("Invalid homeicon: " + homemarker);
                    homeicon = markerapi.getMarkerIcon("blueicon");
                }
            }
        }

        AreaStyle(FileConfiguration cfg, String path) {
            strokecolor = cfg.getString(path+".strokeColor", "#FF0000");
            strokeopacity = cfg.getDouble(path+".strokeOpacity", 0.8);
            strokeweight = cfg.getInt(path+".strokeWeight", 3);
            fillcolor = cfg.getString(path+".fillColor", "#FF0000");
            fillopacity = cfg.getDouble(path+".fillOpacity", 0.35);
            homemarker = cfg.getString(path+".homeicon", null);
            if(homemarker != null) {
                homeicon = markerapi.getMarkerIcon(homemarker);
                if(homeicon == null) {
                    severe("Invalid homeicon: " + homemarker);
                    homeicon = markerapi.getMarkerIcon("blueicon");
                }
            }
        }
    }
    
    public static void info(String msg) {
        log.log(Level.INFO, LOG_PREFIX + msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, LOG_PREFIX + msg);
    }

    private class FactionsUpdate implements Runnable {
        public boolean runonce;
        public void run() {
            if(!stop) {
                updateFactions();
                if(!runonce) {
                    getServer().getScheduler().scheduleSyncDelayedTask(DynmapFactionsPlugin.this, this, updperiod);
                }
                else if(pending == this) {
                    pending = null;
                }
            }
        }
    }

    private class PlayerSetUpdate implements Runnable {
        public String faction;
        public PlayerSetUpdate(String fid) {
            faction = fid;
        }
        public void run() {
            if(!stop)
                updatePlayerSet(faction);
        }
    }
    
    private void requestUpdatePlayerSet(String factid) {
        if(playersets)
            getServer().getScheduler().scheduleSyncDelayedTask(this, new PlayerSetUpdate(factid));
    }

    private FactionsUpdate pending = null;
    
    private void requestUpdateFactions() {
        if(pending == null) {
            FactionsUpdate upd = new FactionsUpdate();
            upd.runonce = true;
            getServer().getScheduler().scheduleSyncDelayedTask(this, upd, 20);
        }
    }

    private void updatePlayerSet(String factid) {
        /* If Wilderness or other unassociated factions, skip */
        if(factid.equals("0") || factid.startsWith("-")) {
            return;
        }
        Set<String> plids = new HashSet<String>();
        Faction f = factapi.get(factid);    /* Get faction */
        if(f != null) {
            Set<FPlayer> ps = f.getFPlayers();
            for(FPlayer fp : ps) {
                plids.add(fp.getId());
            }
            factid = f.getTag();
        }
        String setid = "factions." + factid;
        PlayerSet set = markerapi.getPlayerSet(setid);  /* See if set exists */
        if((set == null) && (f != null)) {
            set = markerapi.createPlayerSet(setid, true, plids, false);
            info("Added player visibility set '" + setid + "' for faction " + factid);
        }
        else if(f != null) {
            set.setPlayers(plids);
        }
        else {
            set.deleteSet();
        }
    }
    
    private Map<String, AreaMarker> resareas = new HashMap<String, AreaMarker>();
    private Map<String, Marker> resmark = new HashMap<String, Marker>();
    
    private String formatInfoWindow(Faction fact) {
        String v = "<div class=\"regioninfo\">"+infowindow+"</div>";
        v = v.replace("%regionname%", ChatColor.stripColor(fact.getTag()));
        v = v.replace("%description%", ChatColor.stripColor(fact.getDescription()));
        FPlayer adm;
        try {
            adm = fact.getFPlayerAdmin();
        } catch (NoSuchMethodError nsme) {
            adm = fact.getFPlayerLeader();
        }
        v = v.replace("%playerowners%", (adm!=null)?adm.getName():"");
        String res = "";
        for(FPlayer r : fact.getFPlayers()) {
        	if(res.length()>0) res += ", ";
        	res += r.getName();
        }
        v = v.replace("%playermembers%", res);
        
        v = v.replace("%nation%", ChatColor.stripColor(fact.getTag()));
        /* Build flags */
        String flgs = "open: " + fact.getOpen();
        try {
            for(FFlag ff : FFlag.values()) {
                flgs += "<br/>" + ff.getNicename() + ": " + fact.getFlag(ff);
                v = v.replace("%flag." + ff.name() + "%", fact.getFlag(ff)?"true":"false");
            }
        } catch (NoClassDefFoundError ncdfe) {
            flgs += "<br/>peaceful: " + fact.isPeaceful();
            v = v.replace("%flag.PEACEFUL%", fact.isPeaceful()?"true":"false");
            flgs += "<br/>peacefulExplosionsEnabled: " + fact.getPeacefulExplosionsEnabled();            
            v = v.replace("%flag.EXPLOSIONS%", fact.getPeacefulExplosionsEnabled()?"true":"false");
        }
        v = v.replace("%flags%", flgs);
        return v;
    }
    
    private boolean isVisible(String id, String worldname) {
        if((visible != null) && (visible.size() > 0)) {
            if((visible.contains(id) == false) && (visible.contains("world:" + worldname) == false)) {
                return false;
            }
        }
        if((hidden != null) && (hidden.size() > 0)) {
            if(hidden.contains(id) || hidden.contains("world:" + worldname))
                return false;
        }
        return true;
    }
        
    private void addStyle(String resid, AreaMarker m) {
        AreaStyle as = cusstyle.get(resid);
        if(as == null) {
            as = defstyle;
        }
        int sc = 0xFF0000;
        int fc = 0xFF0000;
        try {
            sc = Integer.parseInt(as.strokecolor.substring(1), 16);
            fc = Integer.parseInt(as.fillcolor.substring(1), 16);
        } catch (NumberFormatException nfx) {
        }
        m.setLineStyle(as.strokeweight, as.strokeopacity, sc);
        m.setFillStyle(as.fillopacity, fc);
    }
    
    private MarkerIcon getMarkerIcon(String factname, Faction fact) {
        AreaStyle as = cusstyle.get(factname);
        if(as == null) {
            as = defstyle;
        }
        return as.homeicon;
    }
 
    enum direction { XPLUS, ZPLUS, XMINUS, ZMINUS };
        
    /**
     * Find all contiguous blocks, set in target and clear in source
     */
    private int floodFillTarget(TileFlags src, TileFlags dest, int x, int y) {
        int cnt = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<int[]>();
        stack.push(new int[] { x, y });
        
        while(stack.isEmpty() == false) {
            int[] nxt = stack.pop();
            x = nxt[0];
            y = nxt[1];
            if(src.getFlag(x, y)) { /* Set in src */
                src.setFlag(x, y, false);   /* Clear source */
                dest.setFlag(x, y, true);   /* Set in destination */
                cnt++;
                if(src.getFlag(x+1, y))
                    stack.push(new int[] { x+1, y });
                if(src.getFlag(x-1, y))
                    stack.push(new int[] { x-1, y });
                if(src.getFlag(x, y+1))
                    stack.push(new int[] { x, y+1 });
                if(src.getFlag(x, y-1))
                    stack.push(new int[] { x, y-1 });
            }
        }
        return cnt;
    }
    
    private static class FactionBlock {
        int x, z;
    }
    
    private static class FactionBlocks {
        Map<String, LinkedList<FactionBlock>> blocks = new HashMap<String, LinkedList<FactionBlock>>();
    }
    
    /* Handle specific faction on specific world */
    private void handleFactionOnWorld(String factname, Faction fact, String world, LinkedList<FactionBlock> blocks, Map<String, AreaMarker> newmap, Map<String, Marker> newmark) {
        double[] x = null;
        double[] z = null;
        int poly_index = 0; /* Index of polygon for given faction */
        
        /* Build popup */
        String desc = formatInfoWindow(fact);
        
        /* Handle areas */
        if(isVisible(factname, world)) {
        	if(blocks.isEmpty())
        	    return;
            LinkedList<FactionBlock> nodevals = new LinkedList<FactionBlock>();
            TileFlags curblks = new TileFlags();
        	/* Loop through blocks: set flags on blockmaps */
        	for(FactionBlock b : blocks) {
        	    curblks.setFlag(b.x, b.z, true); /* Set flag for block */
        	    nodevals.addLast(b);
        	}
            /* Loop through until we don't find more areas */
            while(nodevals != null) {
                LinkedList<FactionBlock> ournodes = null;
                LinkedList<FactionBlock> newlist = null;
                TileFlags ourblks = null;
                int minx = Integer.MAX_VALUE;
                int minz = Integer.MAX_VALUE;
                for(FactionBlock node : nodevals) {
                    int nodex = node.x;
                    int nodez = node.z;
                    /* If we need to start shape, and this block is not part of one yet */
                    if((ourblks == null) && curblks.getFlag(nodex, nodez)) {
                        ourblks = new TileFlags();  /* Create map for shape */
                        ournodes = new LinkedList<FactionBlock>();
                        floodFillTarget(curblks, ourblks, nodex, nodez);   /* Copy shape */
                        ournodes.add(node); /* Add it to our node list */
                        minx = nodex; minz = nodez;
                    }
                    /* If shape found, and we're in it, add to our node list */
                    else if((ourblks != null) && ourblks.getFlag(nodex, nodez)) {
                        ournodes.add(node);
                        if(nodex < minx) {
                            minx = nodex; minz = nodez;
                        }
                        else if((nodex == minx) && (nodez < minz)) {
                            minz = nodez;
                        }
                    }
                    else {  /* Else, keep it in the list for the next polygon */
                        if(newlist == null) newlist = new LinkedList<FactionBlock>();
                        newlist.add(node);
                    }
                }
                nodevals = newlist; /* Replace list (null if no more to process) */
                if(ourblks != null) {
                    /* Trace outline of blocks - start from minx, minz going to x+ */
                    int init_x = minx;
                    int init_z = minz;
                    int cur_x = minx;
                    int cur_z = minz;
                    direction dir = direction.XPLUS;
                    ArrayList<int[]> linelist = new ArrayList<int[]>();
                    linelist.add(new int[] { init_x, init_z } ); // Add start point
                    while((cur_x != init_x) || (cur_z != init_z) || (dir != direction.ZMINUS)) {
                        switch(dir) {
                            case XPLUS: /* Segment in X+ direction */
                                if(!ourblks.getFlag(cur_x+1, cur_z)) { /* Right turn? */
                                    linelist.add(new int[] { cur_x+1, cur_z }); /* Finish line */
                                    dir = direction.ZPLUS;  /* Change direction */
                                }
                                else if(!ourblks.getFlag(cur_x+1, cur_z-1)) {  /* Straight? */
                                    cur_x++;
                                }
                                else {  /* Left turn */
                                    linelist.add(new int[] { cur_x+1, cur_z }); /* Finish line */
                                    dir = direction.ZMINUS;
                                    cur_x++; cur_z--;
                                }
                                break;
                            case ZPLUS: /* Segment in Z+ direction */
                                if(!ourblks.getFlag(cur_x, cur_z+1)) { /* Right turn? */
                                    linelist.add(new int[] { cur_x+1, cur_z+1 }); /* Finish line */
                                    dir = direction.XMINUS;  /* Change direction */
                                }
                                else if(!ourblks.getFlag(cur_x+1, cur_z+1)) {  /* Straight? */
                                    cur_z++;
                                }
                                else {  /* Left turn */
                                    linelist.add(new int[] { cur_x+1, cur_z+1 }); /* Finish line */
                                    dir = direction.XPLUS;
                                    cur_x++; cur_z++;
                                }
                                break;
                            case XMINUS: /* Segment in X- direction */
                                if(!ourblks.getFlag(cur_x-1, cur_z)) { /* Right turn? */
                                    linelist.add(new int[] { cur_x, cur_z+1 }); /* Finish line */
                                    dir = direction.ZMINUS;  /* Change direction */
                                }
                                else if(!ourblks.getFlag(cur_x-1, cur_z+1)) {  /* Straight? */
                                    cur_x--;
                                }
                                else {  /* Left turn */
                                    linelist.add(new int[] { cur_x, cur_z+1 }); /* Finish line */
                                    dir = direction.ZPLUS;
                                    cur_x--; cur_z++;
                                }
                                break;
                            case ZMINUS: /* Segment in Z- direction */
                                if(!ourblks.getFlag(cur_x, cur_z-1)) { /* Right turn? */
                                    linelist.add(new int[] { cur_x, cur_z }); /* Finish line */
                                    dir = direction.XPLUS;  /* Change direction */
                                }
                                else if(!ourblks.getFlag(cur_x-1, cur_z-1)) {  /* Straight? */
                                    cur_z--;
                                }
                                else {  /* Left turn */
                                    linelist.add(new int[] { cur_x, cur_z }); /* Finish line */
                                    dir = direction.XMINUS;
                                    cur_x--; cur_z--;
                                }
                                break;
                        }
                    }
                    /* Build information for specific area */
                    String polyid = factname + "__" + world + "__" + poly_index;
                    int sz = linelist.size();
                    x = new double[sz];
                    z = new double[sz];
                    for(int i = 0; i < sz; i++) {
                        int[] line = linelist.get(i);
                        x[i] = (double)line[0] * (double)blocksize;
                        z[i] = (double)line[1] * (double)blocksize;
                    }
                    /* Find existing one */
                    AreaMarker m = resareas.remove(polyid); /* Existing area? */
                    if(m == null) {
                        m = set.createAreaMarker(polyid, factname, false, world, x, z, false);
                        if(m == null) {
                            info("error adding area marker " + polyid);
                            return;
                        }
                    }
                    else {
                        m.setCornerLocations(x, z); /* Replace corner locations */
                        m.setLabel(factname);   /* Update label */
                    }
                    m.setDescription(desc); /* Set popup */
                
                    /* Set line and fill properties */
                    addStyle(factname, m);
    
                    /* Add to map */
                    newmap.put(polyid, m);
                    poly_index++;
                }
            }
        }
    }
    
    /* Update Factions information */
    private void updateFactions() {
        Map<String,AreaMarker> newmap = new HashMap<String,AreaMarker>(); /* Build new map */
        Map<String,Marker> newmark = new HashMap<String,Marker>(); /* Build new map */
        
        /* Get board in save format - not good, but best option for traversing the data
         * Map by world, with Map by coordinate pair ("x,z") with value of faction ID */
        Map<String, Map<String, TerritoryAccess>> mapdata = Board.dumpAsSaveFormat();
        /* Parse into faction centric mapping, split by world */
        Map<String, FactionBlocks> blocks_by_faction = new HashMap<String, FactionBlocks>();
        for(Map.Entry<String, Map<String,TerritoryAccess>> me : mapdata.entrySet()) {
            String world = me.getKey();
            for(Map.Entry<String,TerritoryAccess> be : me.getValue().entrySet()) {
                String coord = be.getKey();
                Object ta = be.getValue();
                String fact;
                if(ta instanceof String) {
                    fact = (String)ta;
                }
                else {
                    fact = ((TerritoryAccess)ta).getHostFactionID();
                }
                FactionBlocks factblocks = blocks_by_faction.get(fact); /* Look up faction */
                if(factblocks == null) {    /* Create faction block if first time */
                    factblocks = new FactionBlocks();
                    blocks_by_faction.put(fact, factblocks);
                }
                /* Get block set for given world */
                LinkedList<FactionBlock> blocks = factblocks.blocks.get(world);
                if(blocks == null) {
                    blocks = new LinkedList<FactionBlock>();
                    factblocks.blocks.put(world, blocks);
                }
                /* Parse coords */
                String[] split = coord.split(",");
                if(split.length == 2) {
                    try {
                        FactionBlock fb = new FactionBlock();
                        fb.x = Integer.valueOf(split[0]);
                        fb.z = Integer.valueOf(split[1]);
                        blocks.add(fb); /* Add to list */
                    } catch (NumberFormatException nfx) {
                    }
                }
            }
        }
        /* Loop through factions */
        Collection<Faction> facts = factapi.get();
        for(Faction fact : facts) {
            String factname = ChatColor.stripColor(fact.getTag());
            
            FactionBlocks factblocks = blocks_by_faction.get(fact.getId());
            if(factblocks == null) continue;
            /* Loop through each world that faction has blocks on */
            for(Map.Entry<String, LinkedList<FactionBlock>>  worldblocks : factblocks.blocks.entrySet()) {
                handleFactionOnWorld(factname, fact, worldblocks.getKey(), worldblocks.getValue(), newmap, newmark);
            }
            factblocks.blocks.clear();
            
            /* Now, add marker for home location */
            Location homeloc = fact.getHome();
            if(homeloc != null) {
                String markid = factname + "__home";
                MarkerIcon ico = getMarkerIcon(factname, fact);
                if(ico != null) {
                    Marker home = resmark.remove(markid);
                    String lbl = factname + " [home]";
                    if(home == null) {
                        home = set.createMarker(markid, lbl, homeloc.getWorld().getName(), 
                            homeloc.getX(), homeloc.getY(), homeloc.getZ(), ico, false);
                    }
                    else {
                        home.setLocation(homeloc.getWorld().getName(), homeloc.getX(), homeloc.getY(), homeloc.getZ());
                        home.setLabel(lbl);   /* Update label */
                        home.setMarkerIcon(ico);
                    }
                    home.setDescription(formatInfoWindow(fact)); /* Set popup */
                    newmark.put(markid, home);
                }
            }

        }
        blocks_by_faction.clear();
        
        /* Now, review old map - anything left is gone */
        for(AreaMarker oldm : resareas.values()) {
            oldm.deleteMarker();
        }
        for(Marker oldm : resmark.values()) {
            oldm.deleteMarker();
        }
        /* And replace with new map */
        resareas = newmap;
        resmark = newmark;
                
    }
    
    private void updatePlayerSets() {
        if(playersets) {
            for(Faction f : factapi.get()) {
                updatePlayerSet(f.getId());
            }
        }
    }
    
    private class OurServerListener implements Listener {
        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if(name.equals("dynmap") || name.equals("Factions")) {
                if(dynmap.isEnabled() && factions.isEnabled())
                    activate();
            }
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onFPlayerJoin(FPlayerJoinEvent event) {
            if(event.isCancelled())
                return;
            if(playersets)
                requestUpdatePlayerSet(event.getFaction().getId());
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onFPlayerLeave(FPlayerLeaveEvent event) {
            if(event.isCancelled())
                return;
            if(playersets)
                requestUpdatePlayerSet(event.getFaction().getId());
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onFactionCreate(FactionCreateEvent event) {
            if(event.isCancelled())
                return;
            if(playersets)
                requestUpdatePlayerSet(event.getFactionId());
            requestUpdateFactions();
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onFactionDisband(FactionDisbandEvent event) {
            if(event.isCancelled())
                return;
            if(playersets)
                requestUpdatePlayerSet(event.getFaction().getId());
            requestUpdateFactions();
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onFactionRename(FactionRenameEvent event) {
            if(event.isCancelled())
                return;
            requestUpdateFactions();
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onLandClaim(LandClaimEvent event) {
            if(event.isCancelled())
                return;
            requestUpdateFactions();
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onLandUnclaim(LandUnclaimEvent event) {
            if(event.isCancelled())
                return;
            requestUpdateFactions();
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void onLandUnclaimAll(LandUnclaimAllEvent event) {
            requestUpdateFactions();
        }
    }
    
    public void onEnable() {
        info("initializing");
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI)dynmap; /* Get API */
        /* Get Factions */
        Plugin p = pm.getPlugin("Factions");
        if(p == null) {
            severe("Cannot find Factions!");
            return;
        }
        factions = p;

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);        

        /* If both enabled, activate */
        if(dynmap.isEnabled() && factions.isEnabled())
            activate();
        
        try {
            MetricsLite ml = new MetricsLite(this);
            ml.start();
        } catch (IOException iox) {
        }
    }
    
    private boolean reload = false;
    
    private void activate() {
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading dynmap marker API!");
            return;
        }
        /* Connect to factions API */
        factapi = Factions.i;
        
        blocksize = 16; /* Fixed at 16 */
        
        /* Load configuration */
        if(reload) {
            this.reloadConfig();
            if(set != null) {
                set.deleteMarkerSet();
                set = null;
            }
        }
        else {
            reload = true;
        }
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        
        /* Now, add marker set for mobs (make it transient) */
        set = markerapi.getMarkerSet("factions.markerset");
        if(set == null)
            set = markerapi.createMarkerSet("factions.markerset", cfg.getString("layer.name", "Factions"), null, false);
        else
            set.setMarkerSetLabel(cfg.getString("layer.name", "Factions"));
        if(set == null) {
            severe("Error creating marker set");
            return;
        }
        int minzoom = cfg.getInt("layer.minzoom", 0);
        if(minzoom > 0)
            set.setMinZoom(minzoom);
        set.setLayerPriority(cfg.getInt("layer.layerprio", 10));
        set.setHideByDefault(cfg.getBoolean("layer.hidebydefault", false));
        use3d = cfg.getBoolean("use3dregions", false);
        infowindow = cfg.getString("infowindow", DEF_INFOWINDOW);

        /* Get style information */
        defstyle = new AreaStyle(cfg, "regionstyle");
        cusstyle = new HashMap<String, AreaStyle>();
        ConfigurationSection sect = cfg.getConfigurationSection("custstyle");
        if(sect != null) {
            Set<String> ids = sect.getKeys(false);
            
            for(String id : ids) {
                cusstyle.put(id, new AreaStyle(cfg, "custstyle." + id, defstyle));
            }
        }
        List<String> vis = cfg.getStringList("visibleregions");
        if(vis != null) {
            visible = new HashSet<String>(vis);
        }
        List<String> hid = cfg.getStringList("hiddenregions");
        if(hid != null) {
            hidden = new HashSet<String>(hid);
        }
        /* Chec if player sets enabled */
        playersets = cfg.getBoolean("visibility-by-faction", false);
        if(playersets) {
            try {
                if(!api.testIfPlayerInfoProtected()) {
                    playersets = false;
                    info("Dynmap does not have player-info-protected enabled - visibility-by-faction will have no effect");
                }
            } catch (NoSuchMethodError x) {
                playersets = false;
                info("Dynmap does not support function needed for 'visibility-by-faction' - need to upgrade to 0.60 or later");
            }
        }
        updatePlayerSets();

        /* Set up update job - based on periond */
        int per = cfg.getInt("update.period", 300);
        if(per < 15) per = 15;
        updperiod = (per*20);
        stop = false;
        
        getServer().getScheduler().scheduleSyncDelayedTask(this, new FactionsUpdate(), 40);   /* First time is 2 seconds */
        
        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        if(set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        resareas.clear();
        stop = true;
    }

}
