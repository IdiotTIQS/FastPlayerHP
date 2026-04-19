package cn.brocraft.fastPlayerHP.service;

import cn.brocraft.fastPlayerHP.FastPlayerHP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HealthDisplayService {

    private static final String BELOW_NAME_OBJECTIVE_KEY = "fphp_health";

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

    public enum RenderMode {
        ARMORSTAND,
        BELOW_NAME,
        PROTOCOLLIB;

        public static RenderMode fromInput(String input) {
            if (input == null) {
                return ARMORSTAND;
            }
            return switch (input.toLowerCase()) {
                case "belowname", "below_name", "below-name", "scoreboard" -> BELOW_NAME;
                case "protocollib", "protocol_lib", "packets" -> PROTOCOLLIB;
                default -> ARMORSTAND;
            };
        }
    }

    private final FastPlayerHP plugin;
    private final NamespacedKey ownerKey;

    private final Map<UUID, UUID> ownerToStand = new HashMap<>();
    private final ScoreboardManager scoreboardManager;
    private final Map<Scoreboard, String> previousBelowNameByBoard = new IdentityHashMap<>();
    private final Set<Scoreboard> belowNameOwnedBoards = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private DisplayMode displayMode = DisplayMode.HEARTS_AND_MAX;
    private RenderMode renderMode = RenderMode.ARMORSTAND;
    private double topOffset = 0.35D;
    private double visibleDistance = 32.0D;
    private long pollIntervalTicks = 20L;
    private String heartSymbol = "&c❤";
    private String belowNameTitle = "&c❤";
    private String heartsFormat = "{heart} &f{health}";
    private String heartsAndMaxFormat = "{heart} &f{health}&7/&f{max_health}";
    private int healthDecimals = 1;
    private boolean enabled = true;
    private boolean protocolLibAvailable = false;
    public HealthDisplayService(FastPlayerHP plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.scoreboardManager = Bukkit.getScoreboardManager();
    }

    public void loadSettings() {
        enabled = plugin.getConfig().getBoolean("enabled", true);
        renderMode = RenderMode.fromInput(plugin.getConfig().getString("render-mode", "below_name"));
        topOffset = plugin.getConfig().getDouble("top-offset", 0.35D);
        visibleDistance = Math.max(4.0D, plugin.getConfig().getDouble("visible-distance", 32.0D));
        pollIntervalTicks = Math.max(10L, plugin.getConfig().getLong("poll-interval-ticks", 20L));
        displayMode = DisplayMode.fromInput(plugin.getConfig().getString("display-mode", "hearts_and_max"));
        heartSymbol = plugin.getConfig().getString("text.heart-symbol", "&c❤");
        belowNameTitle = plugin.getConfig().getString("text.below-name-title", "&c❤");
        heartsFormat = plugin.getConfig().getString("text.hearts-format", "{heart} &f{health}");
        heartsAndMaxFormat = plugin.getConfig().getString("text.hearts-and-max-format", "{heart} &f{health}&7/&f{max_health}");
        healthDecimals = Math.max(0, Math.min(2, plugin.getConfig().getInt("text.health-decimals", 1)));
        protocolLibAvailable = isProtocolLibPresent();

        if (renderMode == RenderMode.PROTOCOLLIB && !protocolLibAvailable) {
            plugin.getLogger().warning("ProtocolLib render mode requested but ProtocolLib is not loaded. Falling back to below_name.");
            renderMode = RenderMode.BELOW_NAME;
        }

        if (!enabled) {
            cleanup();
            return;
        }

        cleanup();
        if (isScoreboardRenderActive()) {
            ensureBelowNameObjective();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(player);
        }
    }

    public long getPollIntervalTicks() {
        return pollIntervalTicks;
    }

    public void bootstrapOnlinePlayers() {
        if (!enabled) {
            return;
        }
        if (isScoreboardRenderActive()) {
            ensureBelowNameObjective();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(player);
        }
    }

    public void cleanup() {
        cleanupArmorStands();
        cleanupBelowNameObjective();
    }

    private void cleanupArmorStands() {
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

        if (isScoreboardRenderActive()) {
            ensureBelowNameObjective();
            updateBelowNameScore(player);
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
        if (isScoreboardRenderActive()) {
            Player player = Bukkit.getPlayer(playerId);
            clearBelowNameScore(player);
            return;
        }

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

        if (isScoreboardRenderActive()) {
            updateBelowNameScore(player);
            return;
        }

        ensureStand(player);
        updateStandText(player);
    }

    public void onPlayerChangedWorld(Player player) {
        if (!enabled) {
            return;
        }

        if (isScoreboardRenderActive()) {
            updateBelowNameScore(player);
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

        if (isScoreboardRenderActive()) {
            ensureBelowNameObjective();
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateBelowNameScore(player);
            }
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

        if (isScoreboardRenderActive()) {
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

    public RenderMode getRenderMode() {
        return renderMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
        plugin.getConfig().set("display-mode", displayMode.name().toLowerCase());
        plugin.saveConfig();

        if (isScoreboardRenderActive()) {
            ensureBelowNameObjective();
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateBelowNameScore(player);
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateStandText(player);
        }
    }

    public void setRenderMode(RenderMode nextRenderMode) {
        if (nextRenderMode == RenderMode.PROTOCOLLIB && !isProtocolLibPresent()) {
            nextRenderMode = RenderMode.BELOW_NAME;
        }

        cleanup();
        this.renderMode = nextRenderMode;
        plugin.getConfig().set("render-mode", nextRenderMode.name().toLowerCase());
        plugin.saveConfig();
        bootstrapOnlinePlayers();
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

    public boolean isProtocolLibAvailable() {
        return protocolLibAvailable;
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

        ArmorStand stand = owner.getWorld().spawn(buildTargetLocation(owner), ArmorStand.class, armorStand -> {
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

    private boolean isScoreboardRenderActive() {
        return renderMode == RenderMode.BELOW_NAME || renderMode == RenderMode.PROTOCOLLIB;
    }

    private boolean isProtocolLibPresent() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        return pluginManager != null && pluginManager.isPluginEnabled("ProtocolLib");
    }

    private void ensureBelowNameObjective() {
        if (scoreboardManager == null) {
            return;
        }

        for (Scoreboard board : getActiveScoreboards()) {
            Objective active = board.getObjective(DisplaySlot.BELOW_NAME);
            Objective objective = board.getObjective(BELOW_NAME_OBJECTIVE_KEY);

            if (objective == null) {
                if (active != null && !BELOW_NAME_OBJECTIVE_KEY.equals(active.getName())) {
                    previousBelowNameByBoard.putIfAbsent(board, active.getName());
                }
                objective = board.registerNewObjective(BELOW_NAME_OBJECTIVE_KEY, Criteria.DUMMY, toComponent(belowNameTitle), RenderType.HEARTS);
                belowNameOwnedBoards.add(board);
            } else {
                objective.displayName(toComponent(belowNameTitle));
                objective.setRenderType(RenderType.HEARTS);
            }

            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
    }

    private void updateBelowNameScore(Player player) {
        if (player == null || !player.isOnline() || scoreboardManager == null) {
            return;
        }

        int score = Math.max(0, (int) Math.round(player.getHealth()));
        for (Scoreboard board : getActiveScoreboards()) {
            Objective objective = board.getObjective(BELOW_NAME_OBJECTIVE_KEY);
            if (objective == null) {
                ensureBelowNameObjective();
                objective = board.getObjective(BELOW_NAME_OBJECTIVE_KEY);
            }
            if (objective != null) {
                objective.getScore(player.getName()).setScore(score);
            }
        }
    }

    private void clearBelowNameScore(Player player) {
        if (player == null || scoreboardManager == null) {
            return;
        }

        for (Scoreboard board : getActiveScoreboards()) {
            Objective objective = board.getObjective(BELOW_NAME_OBJECTIVE_KEY);
            if (objective != null) {
                objective.getScore(player.getName()).resetScore();
            }
        }
    }

    private void cleanupBelowNameObjective() {
        if (scoreboardManager == null) {
            return;
        }

        Set<Scoreboard> boards = getActiveScoreboards();
        boards.addAll(belowNameOwnedBoards);
        boards.addAll(previousBelowNameByBoard.keySet());

        for (Scoreboard board : boards) {
            Objective ours = board.getObjective(BELOW_NAME_OBJECTIVE_KEY);
            if (ours != null) {
                ours.unregister();
            }

            String previousName = previousBelowNameByBoard.get(board);
            if (previousName != null) {
                Objective previous = board.getObjective(previousName);
                if (previous != null) {
                    previous.setDisplaySlot(DisplaySlot.BELOW_NAME);
                }
            }
        }

        belowNameOwnedBoards.clear();
        previousBelowNameByBoard.clear();
    }

    private Set<Scoreboard> getActiveScoreboards() {
        Set<Scoreboard> boards = new LinkedHashSet<>();
        if (scoreboardManager != null) {
            Scoreboard main = scoreboardManager.getMainScoreboard();
            if (main != null) {
                boards.add(main);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board != null) {
                boards.add(board);
            }
        }

        return boards;
    }

    private void updateStandPosition(Player owner) {
        ArmorStand stand = getStand(owner);
        if (stand == null) {
            return;
        }

        Location target = buildTargetLocation(owner);
        if (!stand.getWorld().getUID().equals(target.getWorld().getUID())) {
            stand.remove();
            ownerToStand.remove(owner.getUniqueId());
            ensureStand(owner);
            return;
        }

        // Always sync each tick to avoid visual stepping when players move quickly.
        stand.teleport(target);
    }

    private Location buildTargetLocation(Player owner) {
        Location base = owner.getLocation();
        double y = owner.getBoundingBox().getMaxY() + topOffset;
        return new Location(base.getWorld(), base.getX(), y, base.getZ(), base.getYaw(), base.getPitch());
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

    private Component toComponent(String input) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input == null ? "" : input);
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

