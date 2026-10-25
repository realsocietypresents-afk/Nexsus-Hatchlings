package com.nexuscraft.nexushatchlings;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Eggs going down on a fire, dyes, climbing on, the air-click that starts and stops a flight, and keeping dragons from breaking things. */
public final class DragonListener implements Listener {

    private final DragonConfig cfg;
    private final DragonService dragons;
    private final DragonEggs eggs;

    public DragonListener(DragonConfig cfg, DragonService dragons, DragonEggs eggs) {
        this.cfg = cfg;
        this.dragons = dragons;
        this.eggs = eggs;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!cfg.enabled || block == null || block.getType() != Material.DRAGON_EGG) {
            return;
        }
        eggs.register(block);
        Player player = event.getPlayer();
        if (player != null && DragonEggs.heated(block)) {
            player.sendMessage(DragonText.PREFIX + "The egg warms on the fire. It should hatch in about " + eggs.minutesLeft(block)
                    + " minute(s). Keep the fire lit.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!cfg.enabled) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType() == Material.DRAGON_EGG
                && (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)) {
            if (DragonEggs.heated(clicked)) {
                // An egg that is hatching must not jump away when somebody pokes it.
                event.setCancelled(true);
                eggs.register(clicked);
                if (event.getHand() == EquipmentSlot.HAND) {
                    player.sendMessage(DragonText.PREFIX + "The egg is warm: " + Math.round(eggs.progress(clicked) * 100.0)
                            + "% of the way, about " + eggs.minutesLeft(clicked) + " minute(s) to go.");
                }
            }
            return;
        }
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && event.getHand() == EquipmentSlot.HAND) {
            EnderDragon ridden = dragons.riddenBy(player);
            if (ridden != null) {
                Boolean flying = dragons.toggleRide(player);
                event.setCancelled(true);
                if (flying != null) {
                    player.sendMessage(DragonText.PREFIX + (flying ? "Flying. Look where you want to go; right-click again to hover."
                            : "Hovering. Right-click to fly again."));
                }
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!cfg.enabled || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        EnderDragon dragon = dragons.dragonOf(event.getRightClicked());
        if (dragon == null) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);

        ItemStack held = player.getInventory().getItemInMainHand();
        DragonColor dye = held == null ? null : DragonColor.byDye(held.getType().name());
        if (dye != null) {
            if (!dragons.mayRecolor(player, dragon)) {
                player.sendMessage(DragonText.PREFIX + "That dragon belongs to someone else.");
                return;
            }
            if (dragons.colorOf(dragon) == dye) {
                player.sendMessage(DragonText.PREFIX + "It is already " + dye.display().toLowerCase() + ".");
                return;
            }
            dragons.setColor(dragon, dye);
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
                player.getInventory().setItemInMainHand(held.getAmount() <= 0 ? new ItemStack(Material.AIR) : held);
            }
            player.sendMessage(DragonText.PREFIX + "The dragon turns " + dye.display().toLowerCase() + ".");
            return;
        }
        if (player.isSneaking()) {
            player.sendMessage(DragonText.PREFIX + dragons.describe(dragon));
            return;
        }
        DragonService.MountResult result = dragons.mount(player, dragon);
        if (result == DragonService.MountResult.OK) {
            player.sendMessage(DragonText.PREFIX + "You climb on. Right-click to start flying and look where you want to go; sneak to get off.");
        } else {
            player.sendMessage(DragonText.mountFailure(result));
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (dragons.isDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (dragons.isDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        if (dragons.isDragon(dead)) {
            // A vanilla dragon pays out a huge pile of experience; one of these is a pet.
            event.setDroppedExp(0);
            dragons.onDeath(dead);
        }
    }
}
