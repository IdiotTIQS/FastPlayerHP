package cn.brocraft.fastPlayerHP.service;

import cn.brocraft.fastPlayerHP.FastPlayerHP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HealthDisplayService {

    public enum DisplayMode {
        HEARTS,
        HEARTS_AND_MAX;

        public static DisplayMode fromInput(String input) {
            if (input == null) {
                return HEARTS_AND_MAX;
            }
            return switch (input.toLowerCase()) {
                case "hearts", "heart", "simple" -> HEARTS;
                case "full", "number", "hearts_and_max", "mixed" -> HEARTS_AND_MAX;
                default -> HEARTS_AND_MAX;
            };
        }

        public DisplayMode toggle() {
            return this == HEARTS ? HEARTS_AND_MAX : HEARTS;
        }
    }

    private final FastPlayerHP plugin;
    private final NamespacedKey ownerKey;

    private final Map<UUID, UUID> ownerToStand = new HashMap<>();

    private DisplayMode displayMode = DisplayMode.HEARTS_AND_MAX;
    private double yOffset = 2.2D;
    private double visibleDistance = 32.0D;
    private long pollIntervalTicks = 20L;
    private String heartSymbol = "&c❤";
    private String heartsFormat = "{heart} &f{health}";
    private String heartsAndMaxFormat = "{heart} &f{health}&7/&f{max_health}";
    private int healthDecimals = 1;
    private boolean enabled = true;

    public HealthDisplayService(FastPlayerHP plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "owner");
    }

    public void loadSettings() {
        enabled = plugin.getConfig().getBoolean("enabled", true);
        yOffset = plugin.getConfig().getDouble("y-offset", 2.2D);
        visibleDistance = Math.max(4.0D, plugin.getConfig().getDouble("visible-distance", 32.0D));
        pollIntervalTicks = Math.max(10L, plugin.getConfig().getLong("poll-interval-ticks", 20L));
        displayMode = DisplayMode.fromInput(plugin.getConfig().getString("display-mode", "hearts_and_max"));
        heartSymbol = plugin.getConfig().getString("text.heart-symbol", "&c❤");
        heartsFormat = plugin.getConfig().getString("text.hearts-format", "{heart} &f{health}");
        heartsAndMaxFormat = plugin.getConfig().getString("text.hearts-and-max-format", "{heart} &f{health}&7/&f{max_health}");
        healthDecimals = Math.max(0, Math.min(2, plugin.getConfig().getInt("text.health-decimals", 1)));

        if (!enabled) {
            cleanup();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureStand(player);
            updateStandText(player);
        }
    }

    public long getPollIntervalTicks() {
        return pollIntervalTicks;
    }

    public void bootstrapOnlinePlayers() {
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureStand(player);
            updateStandText(player);
            updateVisibilityFor(player);
        }
    }

    public void cleanup() {
        for (UUID standId : ownerToStand.values()) {
            Entity entity = Bukkit.getEntity(standId);
            if (entity != null && entity.getType() == EntityType.ARMOR_STAND) {
                entity.remove();
            }
        }
        ownerToStand.clear();
    }

    public void onPlayerJoin(Player player) {
        if (!enabled) {
            return;
        }
        ensureStand(player);
        updateStandText(player);
        updateVisibilityFor(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                updateVisibilityFor(other);
            }
        }
    }

    public void onPlayerQuit(UUID playerId) {
        UUID standId = ownerToStand.remove(playerId);
        if (standId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(standId);
        if (entity != null) {
            entity.remove();
        }
    }

    public void onHealthChanged(Player player) {
        if (!enabled) {
            return;
        }
        ensureStand(player);
        updateStandText(player);
    }

    public void onPlayerChangedWorld(Player player) {
        if (!enabled) {
            return;
        }
        ensureStand(player);
        updateStandPosition(player);
        updateVisibilityFor(player);
    }

    public void tickFallback() {
        if (!enabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureStand(player);
            updateStandPosition(player);
            updateStandText(player);
            updateVisibilityFor(player);
        }
    }

    public void tickFollow() {
        if (!enabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureStand(player);
            updateStandPosition(player);
        }
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
        plugin.getConfig().set("display-mode", displayMode.name().toLowerCase());
        plugin.saveConfig();
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateStandText(player);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("enabled", enabled);
        plugin.saveConfig();

        if (!enabled) {
            cleanup();
            return;
        }

        bootstrapOnlinePlayers();
    }

    private void ensureStand(Player owner) {
        if (!owner.isOnline()) {
            return;
        }

        UUID standId = ownerToStand.get(owner.getUniqueId());
        if (standId != null) {
            Entity existingEntity = Bukkit.getEntity(standId);
            if (existingEntity instanceof ArmorStand stand && !stand.isDead()) {
                return;
            }
        }

        ArmorStand stand = owner.getWorld().spawn(owner.getLocation().add(0.0D, yOffset, 0.0D), ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setMarker(true);
            armorStand.setSmall(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setCanPickupItems(false);
            armorStand.setCustomNameVisible(true);
            armorStand.setSilent(true);
            armorStand.setPersistent(false);

            PersistentDataContainer container = armorStand.getPersistentDataContainer();
            container.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        });

        ownerToStand.put(owner.getUniqueId(), stand.getUniqueId());
    }

    private void updateStandPosition(Player owner) {
        ArmorStand stand = getStand(owner);
        if (stand == null) {
            return;
        }

        Location target = owner.getLocation().add(0.0D, yOffset, 0.0D);
        if (!stand.getWorld().getUID().equals(target.getWorld().getUID())) {
            stand.remove();
            ownerToStand.remove(owner.getUniqueId());
            ensureStand(owner);
            return;
        }

        // Use squared distance to skip tiny teleports and reduce packets.
        if (stand.getLocation().distanceSquared(target) > 0.03D) {
            stand.teleport(target);
        }
    }

    private void updateStandText(Player owner) {
        ArmorStand stand = getStand(owner);
        if (stand == null) {
            return;
        }

        String name = formatHealth(owner);
        if (!name.equals(stand.getCustomName())) {
            stand.setCustomName(name);
        }
    }

    private void updateVisibilityFor(Player targetOwner) {
        ArmorStand stand = getStand(targetOwner);
        if (stand == null) {
            return;
        }

        double maxDistanceSquared = visibleDistance * visibleDistance;
        World ownerWorld = targetOwner.getWorld();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean shouldSee = viewer.getWorld().getUID().equals(ownerWorld.getUID())
                    && viewer.getLocation().distanceSquared(targetOwner.getLocation()) <= maxDistanceSquared;

            if (shouldSee) {
                viewer.showEntity(plugin, stand);
            } else {
                viewer.hideEntity(plugin, stand);
            }
        }
    }

    private String formatHealth(Player player) {
        double health = Math.max(0.0D, player.getHealth());
        double maxHealth = player.getMaxHealth();
        String heart = colorize(heartSymbol);
        String healthText = formatNumber(health);
        String maxHealthText = formatNumber(maxHealth);

        String template = displayMode == DisplayMode.HEARTS ? heartsFormat : heartsAndMaxFormat;
        return applyTemplate(template, heart, healthText, maxHealthText);
    }

    private String applyTemplate(String template, String heart, String healthText, String maxHealthText) {
        String raw = template
                .replace("{heart}", heart)
                .replace("{health}", healthText)
                .replace("{max_health}", maxHealthText);
        return colorize(raw);
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%." + healthDecimals + "f", value);
    }

    private ArmorStand getStand(Player owner) {
        UUID standId = ownerToStand.get(owner.getUniqueId());
        if (standId == null) {
            return null;
        }

        Entity entity = Bukkit.getEntity(standId);
        if (!(entity instanceof ArmorStand stand) || stand.isDead()) {
            ownerToStand.remove(owner.getUniqueId());
            return null;
        }

        return stand;
    }
}

