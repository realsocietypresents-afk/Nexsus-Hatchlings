package com.nexuscraft.nexushatchlings;

import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/** Reads config.yml once (or on /nexushatchlings reload) and exposes it as plain typed getters. */
public final class HatchlingConfig {

    private final JavaPlugin plugin;

    private boolean enabled = true;
    private double startScale = 0.12;
    private double maxScale = 1.0;
    private double daysToGrow = 14.0;
    private int reapplyIntervalMinutes = 5;
    private boolean invulnerableWhileGrowing = true;
    private boolean followOwner = true;
    private int followRadiusBlocks = 6;
    private double followSpeed = 1.0;
    private int teleportToOwnerDistanceBlocks = 32;
    private Set<EntityType> excludedMobTypes = EnumSet.noneOf(EntityType.class);
    private boolean milestonesEnabled = true;
    private int[] milestonePercentages = {25, 50, 75, 100};
    private int crystalScanIntervalSeconds = 3;
    private int crystalPickupRadiusBlocks = 3;
    private int crystalExpirySeconds = 120;
    private boolean recipeEnabled = true;
    private boolean endermenStayPut = true;
    private boolean ownerMilkAndShear = true;

    public HatchlingConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        this.enabled = plugin.getConfig().getBoolean("settings.enabled", true);
        this.startScale = plugin.getConfig().getDouble("settings.start-scale", 0.12);
        this.maxScale = plugin.getConfig().getDouble("settings.max-scale", 1.0);
        this.daysToGrow = plugin.getConfig().getDouble("settings.days-to-grow", 14.0);
        this.reapplyIntervalMinutes = plugin.getConfig().getInt("settings.reapply-interval-minutes", 5);
        this.invulnerableWhileGrowing = plugin.getConfig().getBoolean("settings.invulnerable-while-growing", true);
        this.followOwner = plugin.getConfig().getBoolean("settings.follow-owner", true);
        this.followRadiusBlocks = plugin.getConfig().getInt("settings.follow-radius-blocks", 6);
        this.followSpeed = plugin.getConfig().getDouble("settings.follow-speed", 1.0);
        this.teleportToOwnerDistanceBlocks = plugin.getConfig().getInt("settings.teleport-to-owner-distance-blocks", 32);

        Set<EntityType> excluded = EnumSet.noneOf(EntityType.class);
        for (String name : plugin.getConfig().getStringList("excluded-mob-types")) {
            try {
                excluded.add(EntityType.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException badType) {
                plugin.getLogger().log(Level.WARNING,
                        "NexusHatchlings: \"{0}\" in excluded-mob-types isn''t a real entity type, ignoring it.",
                        name);
            }
        }
        this.excludedMobTypes = excluded;

        this.milestonesEnabled = plugin.getConfig().getBoolean("milestones.enabled", true);
        List<Integer> configuredMilestones = plugin.getConfig().getIntegerList("milestones.announce-percentages");
        if (!configuredMilestones.isEmpty()) {
            this.milestonePercentages = configuredMilestones.stream().mapToInt(Integer::intValue).sorted().toArray();
        }

        this.crystalScanIntervalSeconds = plugin.getConfig().getInt("crystal.scan-interval-seconds", 3);
        this.crystalPickupRadiusBlocks = plugin.getConfig().getInt("crystal.pickup-radius-blocks", 3);
        this.crystalExpirySeconds = plugin.getConfig().getInt("crystal.expiry-seconds", 120);

        this.recipeEnabled = plugin.getConfig().getBoolean("recipe.enabled", true);
        this.endermenStayPut = plugin.getConfig().getBoolean("endermen.stay-put", true);
        this.ownerMilkAndShear = plugin.getConfig().getBoolean("owner-interactions.milk-and-shear", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double startScale() {
        return startScale;
    }

    public double maxScale() {
        return maxScale;
    }

    public double daysToGrow() {
        return daysToGrow;
    }

    public int reapplyIntervalMinutes() {
        return reapplyIntervalMinutes;
    }

    public boolean invulnerableWhileGrowing() {
        return invulnerableWhileGrowing;
    }

    public boolean followOwner() {
        return followOwner;
    }

    public int followRadiusBlocks() {
        return followRadiusBlocks;
    }

    public double followSpeed() {
        return followSpeed;
    }

    public int teleportToOwnerDistanceBlocks() {
        return teleportToOwnerDistanceBlocks;
    }

    public boolean isExcluded(EntityType type) {
        return excludedMobTypes.contains(type);
    }

    public boolean milestonesEnabled() {
        return milestonesEnabled;
    }

    public int[] milestonePercentages() {
        return milestonePercentages;
    }

    public int crystalScanIntervalSeconds() {
        return crystalScanIntervalSeconds;
    }

    public int crystalPickupRadiusBlocks() {
        return crystalPickupRadiusBlocks;
    }

    public int crystalExpirySeconds() {
        return crystalExpirySeconds;
    }

    public boolean recipeEnabled() {
        return recipeEnabled;
    }

    /** A tamed Enderman does not teleport away by itself and does not pick up blocks. */
    public boolean endermenStayPut() {
        return endermenStayPut;
    }

    /** An owner can milk, shear and bowl-fill their own tamed animals even if another plugin cancelled the click. */
    public boolean ownerMilkAndShear() {
        return ownerMilkAndShear;
    }
}
