package org.dynmap.factions.area;

import com.massivecraft.factions.entity.Faction;
import com.massivecraft.factions.entity.MFlag;
import com.massivecraft.factions.entity.MFlagColl;
import com.massivecraft.factions.entity.MPlayer;
import org.bukkit.ChatColor;
import org.dynmap.factions.TileFlags;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerIcon;

import java.util.ArrayDeque;
import java.util.Map;

public class AreaCommon {


    /**
     * Find all contiguous blocks, set in target and clear in source
     */
    public static int floodFillTarget(TileFlags src, TileFlags dest, int x, int y) {
        int cnt = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<int[]>();
        stack.push(new int[]{x, y});

        while (!stack.isEmpty()) {
            int[] nxt = stack.pop();
            x = nxt[0];
            y = nxt[1];
            if (src.getFlag(x, y)) { /* Set in src */
                src.setFlag(x, y, false);   /* Clear source */
                dest.setFlag(x, y, true);   /* Set in destination */
                cnt++;
                if (src.getFlag(x + 1, y))
                    stack.push(new int[]{x + 1, y});
                if (src.getFlag(x - 1, y))
                    stack.push(new int[]{x - 1, y});
                if (src.getFlag(x, y + 1))
                    stack.push(new int[]{x, y + 1});
                if (src.getFlag(x, y - 1))
                    stack.push(new int[]{x, y - 1});
            }
        }
        return cnt;
    }

    /**
     * Display name of the owner
     *
     * @param faction
     * @return
     */
    private static String getFactionOwner(final Faction faction) {
        for (final MPlayer player : faction.getMPlayers()) {
            if (player.getRank().getName().equalsIgnoreCase(faction.getLeaderRank().getName())) {
                return player.getName();
            }
        }
        return "Unknown";
    }

    public static String formatInfoWindow(final String infoWindow, final Faction faction) {
        String formattedWindow = new StringBuilder("<div class=\"regioninfo\">").append(infoWindow).append("</div>").toString();

        formattedWindow = formattedWindow.replace("%owner%", getFactionOwner(faction));

        formattedWindow = formattedWindow.replace("%regionname%", ChatColor.stripColor(faction.getName()));

        // The describe can be null or empty
        if (faction.getDescription() != null) {
            formattedWindow = formattedWindow.replace("%description%", ChatColor.stripColor(faction.getDescription()));
        }

        final MPlayer adm = faction.getLeader();
        formattedWindow = formattedWindow.replace("%playerowners%", (adm != null) ? adm.getName() : "");

        final StringBuilder res = new StringBuilder();
        for (final MPlayer mPlayer : faction.getMPlayers()) {
            if (res.length() > 0) {
                res.append(", ");
            }
            res.append(mPlayer.getName());
        }

        formattedWindow = formattedWindow.replace("%playermembers%", res.toString());
        formattedWindow = formattedWindow.replace("%nation%", ChatColor.stripColor(faction.getName()));

        // Build flags
        final StringBuilder flgs = new StringBuilder();
        for (final MFlag mFlag : MFlagColl.get().getAll()) {
            flgs.append("<br/>").append(mFlag.getName()).append(": ").append(faction.getFlag(mFlag));
            formattedWindow = formattedWindow.replace(new StringBuilder("%flag.").append(mFlag.getName()).append("%").toString(), String.valueOf(faction.getFlag(mFlag)));
        }
        formattedWindow = formattedWindow.replace("%flags%", flgs.toString());
        return formattedWindow;
    }

    public static void addStyle(final Map<String, AreaStyle> cusstyle, final AreaStyle defstyle, final String resid, final AreaMarker areaMarker) {
        AreaStyle as = cusstyle.get(resid);
        if (as == null) {
            as = defstyle;
        }

        int sc = 0xFF0000;
        int fc = 0xFF0000;
        try {
            sc = Integer.parseInt(as.getStrokecolor().substring(1), 16);
            fc = Integer.parseInt(as.getFillcolor().substring(1), 16);
        } catch (NumberFormatException nfx) {
        }

        areaMarker.setLineStyle(as.getStrokeweight(), as.getStrokeopacity(), sc);
        areaMarker.setFillStyle(as.getFillopacity(), fc);
        areaMarker.setBoostFlag(as.isBoost());
    }

    public static MarkerIcon getMarkerIcon(final Map<String, AreaStyle> cusstyle, final AreaStyle defstyle, final String factionName) {
        AreaStyle as = cusstyle.get(factionName);
        if (as == null) {
            as = defstyle;
        }
        return as.getHomeicon();
    }
}
