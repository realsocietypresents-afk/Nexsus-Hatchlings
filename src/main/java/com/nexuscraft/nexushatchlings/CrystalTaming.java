package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * What happens to a mob a crystal reaches, in one place so the two ways a crystal can reach a mob (a direct hit,
 * and a crystal waiting on the ground) do exactly the same thing: tame it, tell the owner, and when the mob cannot
 * be tamed say why and give the crystal back.
 */
public final class CrystalTaming {

    private final HatchlingConfig config;
    private final HatchlingManager manager;
    private final CrystalItem crystalItem;
    private DragonService dragons;

    public CrystalTaming(HatchlingConfig config, HatchlingManager manager, CrystalItem crystalItem) {
        this.config = config;
        this.manager = manager;
        this.crystalItem = crystalItem;
    }

    /** Lets the taming see how many dragons a player already has (set once at start-up). */
    public void setDragons(DragonService dragons) {
        this.dragons = dragons;
    }

    static String creatureName(Entity entity) {
        return entity.getType().name().toLowerCase().replace('_', ' ');
    }

    /** Tames the mob to its new owner and tells them (if they are online). */
    public void tame(Mob target, UUID ownerId, Location where) {
        if (target instanceof EnderDragon && dragons != null && !dragons.mayTameAnother(ownerId)) {
            giveBack(ownerId, where);
            Player thrower = Bukkit.getPlayer(ownerId);
            if (thrower != null && thrower.isOnline()) {
                thrower.sendMessage(DragonText.PREFIX + "You already keep " + dragons.ownedCount(ownerId) + " dragons, which is the most one player can have ("
                        + dragons.limit() + "). Let one go from /dragon menu to make room. The crystal came back to you.");
            }
            return;
        }
        List<TraitDefinition> rolledTraits = manager.tame(target, ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        String creature = creatureName(target);
        owner.sendMessage("§d[NexusHatchlings] §fTamed a " + creature + " at "
                + where.getBlockX() + ", " + where.getBlockY() + ", " + where.getBlockZ()
                + " -- it'll grow up over time, staying loyal to you.");
        if (!rolledTraits.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (TraitDefinition trait : rolledTraits) {
                if (names.length() > 0) {
                    names.append("§7, §d");
                }
                names.append(trait.displayName());
            }
            owner.sendMessage("§d[NexusHatchlings] §fIt rolled: §d" + names
                    + " §7(§f/nexushatchlings traits §7for details)");
        }
    }

    /**
     * A crystal hit a mob that cannot be tamed (it already has an owner, or its type is excluded in the config):
     * tell the thrower why and hand the crystal back instead of letting it tame some other mob that happens to be near.
     */
    public void refuse(Mob target, UUID throwerId, Location where) {
        String creature = creatureName(target);
        String why;
        if (manager.isTame(target)) {
            UUID owner = manager.ownerOf(target);
            why = throwerId.equals(owner)
                    ? "That " + creature + " is already your hatchling."
                    : "That " + creature + " already belongs to someone else.";
        } else {
            why = "A " + creature + " can't be tamed here.";
        }
        giveBack(throwerId, where);
        Player thrower = Bukkit.getPlayer(throwerId);
        if (thrower != null && thrower.isOnline()) {
            thrower.sendMessage("§d[NexusHatchlings] §f" + why + " The crystal came back to you.");
        }
    }

    private void giveBack(UUID throwerId, Location where) {
        ItemStack crystal = crystalItem.create();
        Player thrower = Bukkit.getPlayer(throwerId);
        if (thrower != null && thrower.isOnline()) {
            for (ItemStack left : thrower.getInventory().addItem(crystal).values()) {
                where.getWorld().dropItemNaturally(thrower.getLocation(), left);
            }
        } else if (where.getWorld() != null) {
            where.getWorld().dropItemNaturally(where, crystal);
        }
    }

    /** Whether the config lets this mob be tamed at all (used by the refusal message). */
    boolean typeAllowed(Entity entity) {
        return !config.isExcluded(entity.getType());
    }
}
