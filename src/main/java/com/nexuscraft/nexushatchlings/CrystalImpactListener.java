package com.nexuscraft.nexushatchlings;

import org.bukkit.Location;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.UUID;

/**
 * Where a thrown crystal lands.
 *
 * <p>A crystal that hits a mob head-on tames exactly that mob, at once. It must not wait for a scan a few seconds
 * later: an Enderman teleports away the moment it is hit, and any animal wanders or flees, so by the time a scan ran
 * the mob was gone (or some other mob had walked up next to the crystal and was tamed instead). If the mob hit cannot
 * be tamed (it is already someone's, or its type is excluded) the thrower is told and gets the crystal back.
 *
 * <p>A crystal that lands on a block, or on something that is not a mob, leaves behind a marked, undespawning item
 * and {@link CrystalScanTask} tames the nearest eligible mob that comes close to it, as before.
 */
public final class CrystalImpactListener implements Listener {

    private final HatchlingKeys keys;
    private final HatchlingManager manager;
    private final CrystalTaming taming;

    public CrystalImpactListener(HatchlingKeys keys, HatchlingManager manager, CrystalTaming taming) {
        this.keys = keys;
        this.manager = manager;
        this.taming = taming;
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball)) {
            return;
        }

        PersistentDataContainer projectilePdc = projectile.getPersistentDataContainer();
        if (!projectilePdc.has(keys.crystalMarker, HatchlingKeys.STRING)) {
            return;
        }

        String thrownBy = projectilePdc.get(keys.thrownBy, HatchlingKeys.STRING);
        Location dropLocation = projectile.getLocation();
        if (dropLocation == null || dropLocation.getWorld() == null) {
            return;
        }

        UUID thrower = parseUuid(thrownBy);
        Entity hit = event.getHitEntity();
        if (hit instanceof EnderDragonPart part) {
            // A dragon is hit through one of its body parts; the mob that gets tamed is the dragon itself.
            hit = part.getParent();
        }
        if (thrower != null && hit instanceof Mob mob) {
            Location where = mob.getLocation() == null ? dropLocation : mob.getLocation();
            if (manager.isEligible(mob)) {
                taming.tame(mob, thrower, where);
            } else {
                taming.refuse(mob, thrower, where);
            }
            projectile.remove();
            return;
        }

        Item dropped = dropLocation.getWorld().dropItem(dropLocation, new CrystalItem(keys).create());
        dropped.setUnlimitedLifetime(true);
        dropped.setCustomName("§dHatchling Crystal");
        dropped.setCustomNameVisible(true);

        PersistentDataContainer itemPdc = dropped.getPersistentDataContainer();
        itemPdc.set(keys.crystalMarker, HatchlingKeys.STRING, "true");
        itemPdc.set(keys.droppedAt, HatchlingKeys.LONG, System.currentTimeMillis());
        if (thrownBy != null) {
            itemPdc.set(keys.thrownBy, HatchlingKeys.STRING, thrownBy);
        }
        projectile.remove();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }
}
