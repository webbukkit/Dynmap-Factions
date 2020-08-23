package org.dynmap.factions;

import com.massivecraft.factions.event.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import static org.dynmap.factions.commons.Constant.DYNMAP_PLUGIN_NAME;
import static org.dynmap.factions.commons.Constant.FACTION_PLUGIN_NAME;

public class OurServerListener implements Listener {

    private final DynmapFactionsPlugin kernel;

    public OurServerListener(final DynmapFactionsPlugin kernel) {
        this.kernel = kernel;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        final Plugin plugin = event.getPlugin();
        final String name = plugin.getDescription().getName();
        if (DYNMAP_PLUGIN_NAME.equals(name) || FACTION_PLUGIN_NAME.equals(name)) {
            if (kernel.getDynmap().isEnabled() && kernel.getFactions().isEnabled()) {
                kernel.activate();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFPlayerJoin(EventFactionsMembershipChange event) {
        if (event.isCancelled())
            return;
        if (kernel.isPlayersets())
            kernel.requestUpdatePlayerSet(event.getNewFaction().getId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFactionCreate(EventFactionsCreate event) {
        if (event.isCancelled())
            return;
        if (kernel.isPlayersets())
            kernel.requestUpdatePlayerSet(event.getFactionId());
        kernel.requestUpdateFactions();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFactionDisband(EventFactionsDisband event) {
        if (event.isCancelled())
            return;
        if (kernel.isPlayersets())
            kernel.requestUpdatePlayerSet(event.getFactionId());
        kernel.requestUpdateFactions();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFactionNameChange(EventFactionsNameChange event) {
        if (event.isCancelled())
            return;
        kernel.requestUpdateFactions();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFactionWarpAdd(EventFactionsWarpAdd event) {
        if (event.isCancelled())
            return;
        kernel.requestUpdateFactions();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFactionWarpRemove(EventFactionsWarpRemove event) {
        if (event.isCancelled())
            return;
        kernel.requestUpdateFactions();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFactionChunksChange(EventFactionsChunksChange event) {
        if (event.isCancelled())
            return;
        kernel.requestUpdateFactions();
    }
}
