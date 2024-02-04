package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;

public class PlayerJoinEvent implements Listener {
    PlayerHashMapManagement playerHashMapManagement;

    public static HashMap<String, Long> hashMap = new HashMap<>();

    public PlayerJoinEvent(PlayerHashMapManagement playerHashMapManagement) {
        this.playerHashMapManagement = playerHashMapManagement;
    }

    @EventHandler
    private void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        event.getPlayer().setFlying(true);
        hashMap.put(event.getPlayer().getName(), System.currentTimeMillis());
        if (playerHashMapManagement.containsPlayer(event.getPlayer())) {
            event.getPlayer().chat("/tpll " + playerHashMapManagement.getCoordinates(event.getPlayer()));
            playerHashMapManagement.removePlayer(event.getPlayer());
        }
    }
}
