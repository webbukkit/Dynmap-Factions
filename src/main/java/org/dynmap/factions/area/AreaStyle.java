package org.dynmap.factions.area;


import org.bukkit.configuration.file.FileConfiguration;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;

import static org.dynmap.factions.DynmapFactionsPlugin.severe;

public class AreaStyle {
    private final String strokecolor;
    private final double strokeopacity;
    private final int strokeweight;
    private final String fillcolor;
    private final double fillopacity;
    private final String homemarker;
    private final boolean boost;

    private MarkerIcon homeicon;

    public AreaStyle(final MarkerAPI markerapi, final FileConfiguration cfg, final String path, final AreaStyle def) {
        strokecolor = cfg.getString(path + ".strokeColor", def != null ? def.strokecolor : "#FF0000");
        strokeopacity = cfg.getDouble(path + ".strokeOpacity", def != null ? def.strokeopacity : 0.8);
        strokeweight = cfg.getInt(path + ".strokeWeight", def != null ? def.strokeweight : 3);
        fillcolor = cfg.getString(path + ".fillColor", def != null ? def.fillcolor : "#FF0000");
        fillopacity = cfg.getDouble(path + ".fillOpacity", def != null ? def.fillopacity : 0.35);
        homemarker = cfg.getString(path + ".homeicon", def != null ? def.homemarker : null);

        if (homemarker != null) {
            homeicon = markerapi.getMarkerIcon(homemarker);
            if (homeicon == null) {
                severe(new StringBuilder("Invalid homeicon: ").append(homemarker).toString());
                homeicon = markerapi.getMarkerIcon("blueicon");
            }
        }

        boost = cfg.getBoolean(path + ".boost", def != null && def.boost);
    }

    public AreaStyle(final MarkerAPI markerapi, final FileConfiguration cfg, final String path) {
        this(markerapi, cfg, path, null);
    }

    public String getStrokecolor() {
        return strokecolor;
    }

    public double getStrokeopacity() {
        return strokeopacity;
    }

    public int getStrokeweight() {
        return strokeweight;
    }

    public String getFillcolor() {
        return fillcolor;
    }

    public double getFillopacity() {
        return fillopacity;
    }

    public String getHomemarker() {
        return homemarker;
    }

    public boolean isBoost() {
        return boost;
    }

    public MarkerIcon getHomeicon() {
        return homeicon;
    }
}