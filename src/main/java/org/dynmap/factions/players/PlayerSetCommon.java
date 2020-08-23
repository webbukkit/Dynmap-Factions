package org.dynmap.factions.players;

import com.massivecraft.factions.entity.Faction;
import com.massivecraft.factions.entity.FactionColl;
import com.massivecraft.factions.entity.MPlayer;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.PlayerSet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.dynmap.factions.DynmapFactionsPlugin.info;
import static org.dynmap.factions.commons.Constant.PREFIX_FACTION_SET_ID;

public class PlayerSetCommon {

    private static final String MESSAGE_ADDED_PLAYER = "Added player visibility set '";
    private static final String MESSAGE_FOR_FACTION = "' for faction ";

    public static void updatePlayerSet(final MarkerAPI markerApi, String factionUUID) {
        /* If Wilderness or other unassociated factions (guid-style ID), skip */
        if (factionUUID.indexOf('-') >= 0) {
            return;
        }
        final Set<String> playerIds = new HashSet<>();
        final FactionColl factionColl = FactionColl.get();

        final Faction faction = factionColl.getByName(factionUUID); // Get faction
        if (faction != null) {
            final List<MPlayer> mPlayers = faction.getMPlayers();
            for (final MPlayer mPlayer : mPlayers) {
                playerIds.add(mPlayer.getId());
            }
            factionUUID = faction.getId();
        }

        final String setId = new StringBuilder(PREFIX_FACTION_SET_ID).append(factionUUID).toString();
        final PlayerSet set = markerApi.getPlayerSet(setId); // See if set exists
        if (set == null && faction != null) {
            markerApi.createPlayerSet(setId, true, playerIds, false);
            info(new StringBuilder(MESSAGE_ADDED_PLAYER).append(setId).append(MESSAGE_FOR_FACTION).append(factionUUID).toString());
        } else if (faction != null) {
            set.setPlayers(playerIds);
        } else {
            set.deleteSet();
        }
    }

    public static void updatePlayerSets(final MarkerAPI markerAPI, final boolean hasPlayer) {
        if (hasPlayer) {
            final FactionColl factionColl = FactionColl.get();
            for (final Faction faction : factionColl.getAll()) {
                if (faction == factionColl.getNone() || faction == factionColl.getWarzone() || faction == factionColl.getSafezone()) {
                    continue;
                }
                updatePlayerSet(markerAPI, faction.getId());
            }
        }
    }
}
