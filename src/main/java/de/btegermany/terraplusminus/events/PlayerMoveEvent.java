package de.btegermany.terraplusminus.events;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import io.papermc.lib.PaperLib;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import static java.lang.String.valueOf;
import static org.bukkit.ChatColor.BOLD;
import static org.bukkit.ChatColor.RED;


public class PlayerMoveEvent implements Listener {

    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    private HashMap<String, Long> teleportCooldown = new HashMap<>();

    private BukkitRunnable runnable;
    private ArrayList<Integer> taskIDs = new ArrayList<>();
    private int yOffset;
    final int yOffsetConfigEntry;

    private final int xOffset;
    private final int zOffset;
    private final boolean linkedWorldsEnabled;

    private final String linkedWorldsMethod;
    private Plugin plugin;
    private List<LinkedWorld> worlds;
    private HashMap<String, Integer> worldHashMap;

    public PlayerMoveEvent(Plugin plugin) {
        this.plugin = plugin;
        this.xOffset = Terraplusminus.config.getInt("terrain_offset.x");
        this.yOffsetConfigEntry = Terraplusminus.config.getInt("terrain_offset.y");
        this.zOffset = Terraplusminus.config.getInt("terrain_offset.z");
        this.linkedWorldsEnabled = Terraplusminus.config.getBoolean("linked_worlds.enabled");
        this.linkedWorldsMethod = Terraplusminus.config.getString("linked_worlds.method");
        this.worldHashMap = new HashMap<>();
        if (this.linkedWorldsEnabled && this.linkedWorldsMethod.equalsIgnoreCase("MULTIVERSE")) {
            this.worlds = ConfigurationHelper.getWorlds();
            for (LinkedWorld world : worlds) {
                this.worldHashMap.put(world.getWorldName(), world.getOffset());
            }
            Bukkit.getLogger().log(Level.INFO, "[T+-] Linked worlds enabled, using Multiverse method.");
        } /*
        else {
            for (World world : Bukkit.getServer().getWorlds()) { // plugin loaded before worlds initialized, so that does not work
                this.worldHashMap.put(world.getName(), yOffsetConfigEntry);
            }
        }
        */
        this.startKeepActionBarAlive();
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        setHeightInActionBar(player);
    }

    private void startKeepActionBarAlive() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                setHeightInActionBar(p);
            }
        }, 0, 20);
    }

    private void setHeightInActionBar(Player p) {
        worldHashMap.putIfAbsent(p.getWorld().getName(), yOffsetConfigEntry);
        if (p.getInventory().getItemInMainHand().getType() != Material.DEBUG_STICK) {
            int height = p.getLocation().getBlockY() - worldHashMap.get(p.getWorld().getName());
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(BOLD + valueOf(height) + "m"));
        }
    }

    @EventHandler
    void onPlayerFall(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!this.linkedWorldsEnabled) {
            return;
        }

        Player p = event.getPlayer();
        World world = p.getWorld();
        Location location = p.getLocation();


        if(this.linkedWorldsMethod.equalsIgnoreCase("MULTIVERSE")){
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Teleport player from world to world
                    if (p.getLocation().getY() < -64.3) {
                        LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(world.getName());
                        if (previousServer != null) {
                            teleportPlayer(previousServer, location, p);
                        }
                    } else if (p.getLocation().getY() > world.getMaxHeight()) {
                        LinkedWorld nextServer = ConfigurationHelper.getNextServerName(world.getName());
                        if (nextServer != null) {
                            teleportPlayer(nextServer, location, p);
                        }
                    }
                }
            }.runTaskLater(plugin, 60L);
        }else if(this.linkedWorldsMethod.equalsIgnoreCase("SERVER") && !p.hasPermission("t+-.noselfteleport")){



            LinkedWorld server = null;

            int yOffset = 0;
            if (p.getLocation().getY() < -64.3) {
                yOffset = 1;
                server = ConfigurationHelper.getWorld(false, p.getLocation().getY());
            } else if (p.getLocation().getY() > world.getMaxHeight()) {
                server = ConfigurationHelper.getWorld(true, p.getLocation().getY());
                yOffset = -1;
            }

            if (server == null) return;

            if(teleportCooldown.containsKey(p.getName())){
                if(teleportCooldown.get(p.getName())+3000 > System.currentTimeMillis()){
                    return;
                }
            }

            if(PlayerJoinEvent.hashMap.get(p.getName())+5000 > System.currentTimeMillis()) return;


            //p.teleport(p.getLocation().add(0, yOffset, 0));
            Location now = p.getLocation();
            Location then = event.getFrom();
            double diffX = (then.getX()-now.getX())*1000;
            double diffY = (then.getY()-now.getY())*1000;
            double diffZ = (then.getZ()-now.getZ())*1000;
            p.teleport(new Location(p.getLocation().getWorld(), event.getFrom().getX()-diffX, event.getFrom().getY()+yOffset-diffY,event.getFrom().getZ()-diffZ));
            teleportCooldown.put(p.getName(), System.currentTimeMillis());
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(server.getWorldName() + "," + server.getOffset());
            double[] mcCoordinates = new double[2];
            mcCoordinates[0] = p.getLocation().getX() - xOffset;
            mcCoordinates[1] = p.getLocation().getZ() - zOffset;
            double[] coordinates;
            try {
                coordinates = bteGeneratorSettings.projection().toGeo(mcCoordinates[0], mcCoordinates[1]);
            } catch (OutOfProjectionBoundsException e) {
                p.sendMessage(RED + "Location is not within projection bounds");
                return;
            }

            out.writeUTF(coordinates[1] + ", " + coordinates[0]);
            p.sendPluginMessage(Terraplusminus.instance, "terraplusminus:teleportbridge", out.toByteArray());

            p.sendMessage(Terraplusminus.config.getString("prefix") + "§cSending to another server...");

        }

        // Verzögerte Teleportation

    }


    private void teleportPlayer(LinkedWorld linkedWorld, Location location, Player p) {
        World tpWorld = Bukkit.getWorld(linkedWorld.getWorldName());
        Location newLocation = new Location(tpWorld, location.getX() + xOffset, tpWorld.getMinHeight(), location.getZ() + zOffset, location.getYaw(), location.getPitch());
        PaperLib.teleportAsync(p, newLocation);
        p.setFlying(true);
        p.sendMessage(Terraplusminus.config.getString("prefix") + "§7You have been teleported to another world.");
    }
}
