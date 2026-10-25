package com.nexuscraft.nexushatchlings;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Milking a tamed cow, shearing a tamed sheep, filling a bowl from a tamed mooshroom: this plugin never gets in the
 * way of those, but a protection or farming plugin on the server may refuse a click on an animal that carries someone
 * else's data. When that happens to the OWNER of the tamed animal, holding a bucket, bowl or shears, the click is
 * allowed again here (this runs last, so it only ever undoes a refusal). Nobody but the owner gets this, and only
 * with those three tools. Turn it off with {@code owner-interactions.milk-and-shear: false}.
 */
public final class OwnerInteractListener implements Listener {

    private final HatchlingConfig config;
    private final HatchlingManager manager;
    private final Consumer<String> info;
    private final Set<String> told = new HashSet<>();

    public OwnerInteractListener(HatchlingConfig config, HatchlingManager manager, Consumer<String> info) {
        this.config = config;
        this.manager = manager;
        this.info = info;
    }

    static boolean isFarmTool(Material type) {
        return type == Material.BUCKET || type == Material.BOWL || type == Material.SHEARS;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!event.isCancelled() || !config.ownerMilkAndShear()) {
            return;
        }
        Entity target = event.getRightClicked();
        Player player = event.getPlayer();
        if (target == null || player == null || target instanceof EnderDragon || !manager.isTame(target)) {
            return;
        }
        UUID owner = manager.ownerOf(target);
        if (owner == null || !owner.equals(player.getUniqueId())) {
            return;
        }
        ItemStack held = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (held == null || !isFarmTool(held.getType())) {
            return;
        }
        event.setCancelled(false);
        // One line per player and animal type per session, so the log shows it is happening without filling up.
        if (told.add(player.getUniqueId() + ":" + target.getType())) {
            info.accept("NexusHatchlings: another plugin refused " + player.getName() + " using a " + held.getType().name().toLowerCase()
                    + " on their own tamed " + target.getType().name().toLowerCase().replace('_', ' ')
                    + "; allowed it. If this keeps showing, the plugin that refuses is not NexusHatchlings.");
        }
    }
}
