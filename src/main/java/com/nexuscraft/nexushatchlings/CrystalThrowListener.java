package com.nexuscraft.nexushatchlings;

import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a right-click with a Hatchling Crystal in hand into a thrown, tagged Snowball -- the same
 * "it's just a snowball underneath" trick NexusWarbeasts' Truce Crystal uses, so it flies, bounces,
 * and lands using real vanilla projectile physics instead of anything hand-rolled.
 */
public final class CrystalThrowListener implements Listener {

    private static final long THROW_COOLDOWN_MILLIS = 250;

    private final HatchlingKeys keys;
    private final CrystalItem crystalItem;
    private final Map<UUID, Long> lastThrowMillis = new HashMap<>();

    public CrystalThrowListener(HatchlingKeys keys, CrystalItem crystalItem) {
        this.keys = keys;
        this.crystalItem = crystalItem;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!crystalItem.isCrystal(item)) {
            return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastThrowMillis.get(player.getUniqueId());
        if (last != null && now - last < THROW_COOLDOWN_MILLIS) {
            // A single physical right-click can fire this event more than once (main hand/off
            // hand, or block vs. air); without this, one throw could otherwise consume two
            // crystals and launch two snowballs.
            return;
        }
        lastThrowMillis.put(player.getUniqueId(), now);

        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        // Take the crystal from the hand it is actually in: with it in the off hand the click arrives as an
        // off-hand event, and clearing the main hand there threw a crystal without using one up.
        boolean offHand = event.getHand() == EquipmentSlot.OFF_HAND;
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else if (offHand) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        Snowball snowball = player.launchProjectile(Snowball.class);
        if (snowball != null) {
            snowball.getPersistentDataContainer().set(keys.crystalMarker, HatchlingKeys.STRING, "true");
            snowball.getPersistentDataContainer().set(keys.thrownBy, HatchlingKeys.STRING, player.getUniqueId().toString());
        }

        player.sendMessage("§d[NexusHatchlings] §fCrystal thrown -- whatever it lands near has a chance to be tamed.");
    }
}
