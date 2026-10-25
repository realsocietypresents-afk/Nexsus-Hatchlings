package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns every tamed hatchling's owner, age, and current size -- all of it read straight off the
 * entity's own PersistentDataContainer, never a separate file. A hatchling that unloads with its
 * chunk and loads back in ten real days later just keeps growing from where its tamed-at timestamp
 * says it should be, the same wall-clock model NexusGrowth uses for players.
 */
public final class HatchlingManager {

    private final JavaPlugin plugin;
    private final HatchlingConfig config;
    private final HatchlingKeys keys;
    private final TraitManager traits;
    private final WaitingBridge waiting;
    private DragonService dragons;
    private boolean selfTeleport;

    public HatchlingManager(JavaPlugin plugin, HatchlingConfig config, HatchlingKeys keys, TraitManager traits) {
        this(plugin, config, keys, traits, new WaitingBridge(plugin.getLogger()::warning));
    }

    HatchlingManager(JavaPlugin plugin, HatchlingConfig config, HatchlingKeys keys, TraitManager traits, WaitingBridge waiting) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.traits = traits;
        this.waiting = waiting;
    }

    /** Lets the dragon system take over dragons that are tamed or released (set once at start-up). */
    public void setDragons(DragonService dragons) {
        this.dragons = dragons;
    }

    private boolean isManagedDragon(Entity entity) {
        return dragons != null && entity instanceof EnderDragon && dragons.isDragon(entity);
    }

    /** The key this hatchling's timer has on the Waiting list (one per creature, stable for its whole life). */
    static String waitingKey(Entity entity) {
        return "hatchling:" + entity.getUniqueId();
    }

    /**
     * Tells NexusWaiting when this hatchling finishes growing (if NexusWaiting is installed). Safe to call over and
     * over: NexusWaiting ignores a timer it already has with the same ready time. Nothing is sent once it is fully
     * grown, so a finished timer the owner cleared is not brought back.
     */
    private void registerTimer(Entity entity, UUID ownerId) {
        Long tamedAt = entity.getPersistentDataContainer().get(keys.tamedAt, HatchlingKeys.LONG);
        if (tamedAt == null) {
            return;
        }
        long readyAt = tamedAt + totalDurationMillis();
        if (readyAt <= System.currentTimeMillis()) {
            return;
        }
        String creature = entity.getType().name().toLowerCase().replace('_', ' ');
        waiting.set(ownerId, waitingKey(entity), "Your tamed " + creature + " has finished growing", readyAt);
    }

    /** Takes a hatchling off the Waiting list: it was released or it died, so it will never finish growing. */
    public void forgetTimer(Entity entity) {
        UUID ownerId = ownerOf(entity);
        if (ownerId != null) {
            waiting.clear(ownerId, waitingKey(entity));
        }
    }

    /** True for any real creature -- hostile, passive, or boss-tier alike -- that isn't already tamed. */
    public boolean isEligible(Entity entity) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        if (config.isExcluded(entity.getType())) {
            return false;
        }
        return !isTame(entity);
    }

    public boolean isTame(Entity entity) {
        return entity.getPersistentDataContainer().has(keys.owner, HatchlingKeys.STRING);
    }

    public UUID ownerOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(keys.owner, HatchlingKeys.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException badUuid) {
            return null;
        }
    }

    /** Shrinks and tames a mob in the same instant -- the crystal's whole effect. Also rolls its
     *  traits (see TraitManager#rollAndStore) in that same instant, once, ever. Returns whatever
     *  traits it rolled so the caller (CrystalScanTask) can announce them in the same chat message
     *  that already announces the taming itself. */
    public List<TraitDefinition> tame(Mob mob, UUID ownerId) {
        mob.getPersistentDataContainer().set(keys.owner, HatchlingKeys.STRING, ownerId.toString());
        mob.getPersistentDataContainer().set(keys.tamedAt, HatchlingKeys.LONG, System.currentTimeMillis());
        mob.getPersistentDataContainer().set(keys.milestone, HatchlingKeys.INTEGER, 0);
        // Once the owner is written the mob IS tamed. Nothing after this line may stop that from being
        // reported: a failure in a side step is logged and the rest still runs.
        List<TraitDefinition> rolled = List.of();
        try {
            applyScale(mob);
            registerTimer(mob, ownerId);
            rolled = traits.rollAndStore(mob);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "NexusHatchlings: a tamed " + mob.getType()
                    + " is tamed, but sizing or rolling its traits failed.", ex);
        }
        if (dragons != null && mob instanceof EnderDragon dragon) {
            try {
                dragons.adopt(dragon, ownerId);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "NexusHatchlings: a tamed dragon could not be set up as a dragon.", ex);
            }
        }
        return rolled;
    }

    /** True while this plugin itself is teleporting a pet to its owner (so a ban on wandering teleports lets it through). */
    public boolean isSelfTeleport() {
        return selfTeleport;
    }

    /** Frees a hatchling -- it keeps whatever size (and whatever stat growth its traits already
     *  phased in) it's grown to, but is no longer owned, protected, or eligible for trait upkeep
     *  (ability potion effects stop being reapplied; FIRE_IMMUNE stops applying too, since that's
     *  gated on the traits tag this clears). */
    public void release(Entity entity) {
        forgetTimer(entity);
        if (isManagedDragon(entity)) {
            dragons.onRelease((EnderDragon) entity);
        }
        entity.getPersistentDataContainer().remove(keys.owner);
        entity.getPersistentDataContainer().remove(keys.tamedAt);
        entity.getPersistentDataContainer().remove(keys.milestone);
        traits.clear(entity);
    }

    long totalDurationMillis() {
        return Math.round(config.daysToGrow() * Duration.ofDays(1).toMillis());
    }

    /** 0.0 (just tamed) .. 1.0 (fully grown). */
    public double progressFraction(Entity entity) {
        Long tamedAt = entity.getPersistentDataContainer().get(keys.tamedAt, HatchlingKeys.LONG);
        if (tamedAt == null) {
            return 0.0;
        }
        long elapsed = System.currentTimeMillis() - tamedAt;
        double fraction = elapsed / (double) totalDurationMillis();
        return Math.max(0.0, Math.min(1.0, fraction));
    }

    public double currentScale(Entity entity) {
        return config.startScale() + (config.maxScale() - config.startScale()) * progressFraction(entity);
    }

    private void applyScale(Entity entity) {
        AttributeInstance scaleAttribute = ((Mob) entity).getAttribute(Attribute.GENERIC_SCALE);
        if (scaleAttribute == null) {
            return;
        }
        scaleAttribute.setBaseValue(currentScale(entity));
    }

    /** Package-private testing seam: lets a standalone test exercise milestone tagging without a real online Player. */
    void announceMilestoneIfReached(Entity entity, UUID ownerId) {
        if (!config.milestonesEnabled()) {
            return;
        }
        Integer already = entity.getPersistentDataContainer().get(keys.milestone, HatchlingKeys.INTEGER);
        int alreadyValue = already == null ? 0 : already;
        int percent = (int) Math.floor(progressFraction(entity) * 100.0);

        int reached = alreadyValue;
        for (int milestone : config.milestonePercentages()) {
            if (percent >= milestone && milestone > alreadyValue) {
                reached = milestone;
            }
        }

        if (reached > alreadyValue) {
            entity.getPersistentDataContainer().set(keys.milestone, HatchlingKeys.INTEGER, reached);
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && owner.isOnline()) {
                String creature = entity.getType().name().toLowerCase().replace('_', ' ');
                if (reached >= 100) {
                    owner.sendMessage("§d[NexusHatchlings] §fYour tamed " + creature
                            + " has finished growing -- full size, still yours.");
                } else {
                    owner.sendMessage("§d[NexusHatchlings] §fYour tamed " + creature
                            + " has grown a little more -- " + reached + "% of the way there.");
                }
            }
        }
    }

    private double distance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void driveFollowOwner(Mob mob, Player owner) {
        if (!config.followOwner()) {
            return;
        }
        // NexusBestiary leaves this note on a pet its owner told to stay or guard a spot: it must not be called back.
        if (mob.getPersistentDataContainer().has(keys.bestiaryHeld, HatchlingKeys.STRING)) {
            return;
        }
        Location mobLocation = mob.getLocation();
        Location ownerLocation = owner.getLocation();
        if (mobLocation == null || ownerLocation == null) {
            return;
        }
        if (mobLocation.getWorld() == null || ownerLocation.getWorld() == null
                || !mobLocation.getWorld().getName().equals(ownerLocation.getWorld().getName())) {
            return;
        }

        double distance = distance(mobLocation, ownerLocation);
        if (distance > config.teleportToOwnerDistanceBlocks()) {
            // Fell into a cave, got split across an unloaded chunk boundary, etc. -- the same
            // safety net vanilla tamed wolves fall back on when they're left too far behind.
            selfTeleport = true;
            try {
                mob.teleport(ownerLocation);
            } finally {
                selfTeleport = false;
            }
        } else if (distance > config.followRadiusBlocks()) {
            mob.getPathfinder().moveTo(owner, config.followSpeed());
        }
    }

    /** Reapplies scale (and, if configured, follow-owner movement) to one already-confirmed-tame mob. */
    public void reapplyOne(Mob mob) {
        UUID ownerId = ownerOf(mob);
        if (ownerId == null) {
            return;
        }
        applyScale(mob);
        traits.applyEffects(mob, progressFraction(mob));
        announceMilestoneIfReached(mob, ownerId);
        registerTimer(mob, ownerId);

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline() && !isManagedDragon(mob)) {
            driveFollowOwner(mob, owner);
        }
    }

    /** Walks every loaded world and reapplies every currently-loaded tamed hatchling it finds. */
    public void reapplyAllLoaded() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob && isTame(entity)) {
                    try {
                        reapplyOne((Mob) entity);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "NexusHatchlings: couldn''t reapply a tamed " + entity.getType()
                                        + ", skipping it this cycle.", e);
                    }
                }
            }
        }
    }
}
