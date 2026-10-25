package com.nexuscraft.nexushatchlings;

import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;

import java.util.UUID;

/**
 * Everything that protects a hatchling relationship once taming is done: an owner is never a valid
 * target or victim for their own tamed creature (two layers, same pattern NexusAdmin and the
 * described NexusWarbeasts both use -- cancel the targeting itself, and cancel the damage too in
 * case a target ever slips through some other way); a still-growing hatchling can't be hurt by
 * anything except the void, if configured that way; and nothing but this plugin's own scan task is
 * ever allowed to pick up a waiting crystal item.
 */
public final class HatchlingListener implements Listener {

    private final HatchlingConfig config;
    private final HatchlingManager manager;
    private final HatchlingKeys keys;

    public HatchlingListener(HatchlingConfig config, HatchlingManager manager, HatchlingKeys keys) {
        this.config = config;
        this.manager = manager;
        this.keys = keys;
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (!manager.isTame(event.getEntity())) {
            return;
        }
        UUID owner = manager.ownerOf(event.getEntity());
        Entity target = event.getTarget();
        if (owner != null && target != null && owner.equals(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity damaged = event.getEntity();
        if (!manager.isTame(damager)) {
            return;
        }
        UUID owner = manager.ownerOf(damager);
        if (owner != null && damaged != null && owner.equals(damaged.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!config.invulnerableWhileGrowing()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!manager.isTame(entity)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (manager.progressFraction(entity) < 1.0) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getItem() == null) {
            return;
        }
        if (event.getItem().getPersistentDataContainer().has(keys.crystalMarker, HatchlingKeys.STRING)) {
            // Only CrystalScanTask is allowed to "consume" a waiting crystal -- vanilla pickup
            // (by a villager, a tamed wolf, a player walking over it, the hatchling itself, etc.)
            // is always cancelled.
            event.setCancelled(true);
        }
    }

    /**
     * A tamed Enderman stays where its owner is. Endermen teleport on their own (when hit, when it rains, at
     * random), which would carry a pet away for good. This plugin's own "come to the owner" teleport is let through.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEndermanTeleport(EntityTeleportEvent event) {
        if (!config.endermenStayPut() || !(event.getEntity() instanceof Enderman)) {
            return;
        }
        if (manager.isTame(event.getEntity()) && !manager.isSelfTeleport()) {
            event.setCancelled(true);
        }
    }

    /** A tamed Enderman does not pick up (or put down) blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onEndermanBlocks(EntityChangeBlockEvent event) {
        if (!config.endermenStayPut() || !(event.getEntity() instanceof Enderman)) {
            return;
        }
        if (manager.isTame(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** A hatchling that dies will never finish growing: take it off its owner's Waiting list. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity != null && manager.isTame(entity)) {
            manager.forgetTimer(entity);
        }
    }
}
