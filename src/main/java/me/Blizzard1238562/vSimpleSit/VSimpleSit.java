package me.Blizzard1238562.vSimpleSit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import javax.net.ssl.HttpsURLConnection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class VSimpleSit extends JavaPlugin implements Listener, CommandExecutor {

    private String sitSound;
    private String sitParticle;
    private String airSitMessage;
    private String airSitParticle;
    private final HashMap<UUID, ArmorStand> sittingMap = new HashMap<>();
    private final String MODRINTH_PROJECT_ID = "simplesit";
    private final String CURRENT_VERSION = "1.1";

    public void onEnable() {
        this.saveDefaultConfig();
        this.loadConfigValues();
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("sit").setExecutor(this);
        this.checkForUpdates();
    }

    public void onDisable() {
        for (ArmorStand stand : this.sittingMap.values()) {
            if (stand.isDead()) continue;
            stand.remove();
        }
        this.sittingMap.clear();
    }

    private void loadConfigValues() {
        FileConfiguration config = this.getConfig();
        this.sitSound = config.getString("sit-sound", "ENTITY_HORSE_SADDLE");
        this.sitParticle = config.getString("sit-particle", "CLOUD");
        this.airSitMessage = config.getString("air-sit-message", "You cannot sit mid-air!");
        this.airSitParticle = config.getString("air-sit-particle", "SMOKE_NORMAL");
    }

    /**
     * Runs on the dedicated Folia/Paper AsyncScheduler instead of the
     * legacy BukkitScheduler, so this stays Folia-compatible.
     */
    private void checkForUpdates() {
        Bukkit.getAsyncScheduler().runNow(this, task -> {
            try {
                URL url = new URL("https://api.modrinth.com/v2/project/" + this.MODRINTH_PROJECT_ID + "/version");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SitPlugin-UpdateChecker");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    JSONParser parser = new JSONParser();
                    JSONArray versions = (JSONArray) parser.parse((Reader) reader);
                    if (!versions.isEmpty()) {
                        JSONObject latest = (JSONObject) versions.get(0);
                        String latestVersion = (String) latest.get("version_number");
                        if (!this.CURRENT_VERSION.equalsIgnoreCase(latestVersion)) {
                            String downloadUrl = "https://modrinth.com/plugin/" + this.MODRINTH_PROJECT_ID;
                            this.getLogger().log(Level.INFO, "A new version of SitPlugin is available: v" + latestVersion);
                            this.getLogger().log(Level.INFO, "Download it from: " + downloadUrl);
                        }
                    }
                }
            } catch (Exception e) {
                this.getLogger().warning("Failed to check for updates on Modrinth.");
            }
        });
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (player.isSneaking()) {
            return;
        }
        if (!player.hasPermission("sit.use")) {
            return;
        }
        Material type = block.getType();
        if (type.name().endsWith("_STAIRS") || type.name().endsWith("_SLAB")) {
            Location sitLoc = block.getLocation().add(0.5, 0.0, 0.5);
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Stairs) {
                Stairs stairs = (Stairs) blockData;
                if (stairs.getHalf() == Bisected.Half.TOP) {
                    return;
                }
                sitLoc.add(0.0, 0.5, 0.0);
            } else if (type.name().endsWith("_SLAB")) {
                sitLoc.add(0.0, 0.5, 0.0);
            }
            this.sitPlayer(player, sitLoc);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking() && this.sittingMap.containsKey(player.getUniqueId())) {
            ArmorStand stand = this.sittingMap.remove(player.getUniqueId());
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player) sender;
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("sit.reload")) {
                player.sendMessage("You do not have permission to reload the config.");
                return true;
            }
            this.reloadConfig();
            this.loadConfigValues();
            player.sendMessage("Sit plugin configuration reloaded.");
            return true;
        }
        if (!player.hasPermission("sit.use")) {
            player.sendMessage("You do not have permission to use /sit");
            return true;
        }
        Location loc = player.getLocation();
        Block blockBeneath = loc.subtract(0.0, 0.1, 0.0).getBlock();
        if (blockBeneath.getType().isAir()) {
            player.sendMessage(this.airSitMessage);
            try {
                Particle particle = Particle.valueOf(this.airSitParticle);
                loc.getWorld().spawnParticle(particle, player.getLocation(), 10, 0.3, 0.2, 0.3);
            } catch (IllegalArgumentException e) {
                this.getLogger().warning("Invalid air-sit-particle in config: " + this.airSitParticle);
            }
            return true;
        }
        this.sitPlayer(player, player.getLocation());
        return true;
    }

    private void sitPlayer(Player player, Location loc) {
        if (this.sittingMap.containsKey(player.getUniqueId())) {
            return;
        }
        ArmorStand chair = (ArmorStand) loc.getWorld().spawn(loc, ArmorStand.class);
        chair.setInvisible(true);
        chair.setGravity(false);
        chair.setInvulnerable(true);
        chair.setMarker(true);
        chair.addPassenger((Entity) player);
        this.sittingMap.put(player.getUniqueId(), chair);
        try {
            Sound sound = Sound.valueOf(this.sitSound);
            player.playSound(loc, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            this.getLogger().warning("Invalid sit-sound in config: " + this.sitSound);
        }
        try {
            Particle particle = Particle.valueOf(this.sitParticle);
            loc.getWorld().spawnParticle(particle, loc, 10);
        } catch (IllegalArgumentException e) {
            this.getLogger().warning("Invalid sit-particle in config: " + this.sitParticle);
        }
    }
}
