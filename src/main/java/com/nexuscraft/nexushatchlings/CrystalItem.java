package com.nexuscraft.nexushatchlings;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * The Hatchling Crystal itself. Identity is the PDC tag, never the display name or lore -- exactly
 * NexusWarbeasts' Truce Crystal, so renaming or anvil-tricking a plain amethyst shard can never
 * forge a fake one.
 */
public final class CrystalItem {

    private final HatchlingKeys keys;

    public CrystalItem(HatchlingKeys keys) {
        this.keys = keys;
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§dHatchling Crystal");
        meta.setLore(Arrays.asList(
                "§7Throw this at any creature.",
                "§7It shrinks down to a tiny hatchling",
                "§7and is tamed to you on the spot.",
                "§7",
                "§7It'll grow up over time --",
                "§7staying loyal to you the whole way."
        ));
        meta.getPersistentDataContainer().set(keys.crystalMarker, HatchlingKeys.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCrystal(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(keys.crystalMarker, HatchlingKeys.STRING);
    }
}
