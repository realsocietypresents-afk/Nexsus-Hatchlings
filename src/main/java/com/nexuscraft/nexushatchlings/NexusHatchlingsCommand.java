package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NexusHatchlingsCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final HatchlingConfig config;
    private final HatchlingManager manager;
    private final CrystalItem crystalItem;
    private final TraitRegistry traitRegistry;
    private final TraitManager traitManager;
    private Runnable reloadHook = () -> { };

    /** Something else to re-read when {@code /nexushatchlings reload} runs (the dragon settings). */
    public void setReloadHook(Runnable hook) {
        this.reloadHook = hook;
    }

    public NexusHatchlingsCommand(JavaPlugin plugin, HatchlingConfig config, HatchlingManager manager,
                                   CrystalItem crystalItem, TraitRegistry traitRegistry, TraitManager traitManager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.crystalItem = crystalItem;
        this.traitRegistry = traitRegistry;
        this.traitManager = traitManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            HatchlingMessages.sendOverview(sender, config, traitRegistry);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                return give(sender, args);
            }
            case "list" -> {
                return list(sender);
            }
            case "release" -> {
                return release(sender, args);
            }
            case "traits" -> {
                return traits(sender);
            }
            case "reload" -> {
                return reload(sender);
            }
            default -> {
                sender.sendMessage(usage());
                return true;
            }
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("nexushatchlings.admin")) {
                sender.sendMessage("§cYou can only give yourself a crystal -- nexushatchlings.admin lets you give one to someone else.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cThat player isn't online.");
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cConsole has no inventory -- specify a player: /nexushatchlings give <player>");
                return true;
            }
            if (!sender.hasPermission("nexushatchlings.use")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            target = player;
        }

        target.getInventory().addItem(crystalItem.create());
        target.sendMessage("§d[NexusHatchlings] §fYou received a Hatchling Crystal.");
        if (sender != target) {
            sender.sendMessage("§a[NexusHatchlings] §fGave " + target.getName() + " a Hatchling Crystal.");
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole has no hatchlings of its own -- run this in-game.");
            return true;
        }

        UUID id = player.getUniqueId();
        List<String> lines = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob && manager.isTame(entity) && id.equals(manager.ownerOf(entity))) {
                    Location location = entity.getLocation();
                    double percent = manager.progressFraction(entity) * 100.0;
                    String where = location == null ? "?" : location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
                    String traitNames = traitSummary(entity);
                    lines.add("§7 - §f" + entity.getType().name().toLowerCase().replace('_', ' ')
                            + " §7(" + String.format("%.0f", percent) + "% grown, at " + where + ")"
                            + (traitNames.isEmpty() ? "" : "\n§8    Traits: §d" + traitNames));
                }
            }
        }

        if (lines.isEmpty()) {
            sender.sendMessage("§d[NexusHatchlings] §fYou don't have any tamed hatchlings loaded right now.");
            return true;
        }

        sender.sendMessage("§d[NexusHatchlings] §fYour tamed hatchlings:");
        for (String line : lines) {
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean release(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole has no hatchlings of its own -- run this in-game.");
            return true;
        }

        boolean all = args.length >= 2 && args[1].equalsIgnoreCase("all");
        UUID id = player.getUniqueId();
        Location playerLocation = player.getLocation();

        Entity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        int releasedCount = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Mob) || !manager.isTame(entity) || !id.equals(manager.ownerOf(entity))) {
                    continue;
                }
                if (all) {
                    manager.release(entity);
                    releasedCount++;
                    continue;
                }
                Location entityLocation = entity.getLocation();
                if (entityLocation == null || playerLocation == null || entityLocation.getWorld() == null
                        || playerLocation.getWorld() == null
                        || !entityLocation.getWorld().getName().equals(playerLocation.getWorld().getName())) {
                    continue;
                }
                double dx = entityLocation.getX() - playerLocation.getX();
                double dy = entityLocation.getY() - playerLocation.getY();
                double dz = entityLocation.getZ() - playerLocation.getZ();
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared;
                    nearest = entity;
                }
            }
        }

        if (all) {
            sender.sendMessage(releasedCount == 0
                    ? "§d[NexusHatchlings] §fYou don't have any tamed hatchlings to release."
                    : "§a[NexusHatchlings] §fReleased " + releasedCount + " tamed hatchling(s).");
            return true;
        }

        if (nearest == null) {
            sender.sendMessage("§cNo tamed hatchling of yours is nearby to release.");
            return true;
        }
        manager.release(nearest);
        sender.sendMessage("§a[NexusHatchlings] §fReleased your " + nearest.getType().name().toLowerCase().replace('_', ' ') + ".");
        return true;
    }

    private boolean traits(CommandSender sender) {
        if (traitRegistry.all().isEmpty()) {
            sender.sendMessage("§d[NexusHatchlings] §fNo traits are configured right now.");
            return true;
        }
        sender.sendMessage("§d§lPossible Hatchling Traits §7- each new tame rolls "
                + traitRegistry.countMin() + "-" + traitRegistry.countMax()
                + " of these" + (traitRegistry.enabled() ? "" : " §c(rolling is currently disabled)"));
        for (TraitDefinition definition : traitRegistry.all().values()) {
            String kindLabel = switch (definition.kind()) {
                case STAT -> "§7Stat";
                case ABILITY -> "§7Ability";
                case COSMETIC -> "§7Cosmetic";
            };
            sender.sendMessage(" §d" + definition.displayName() + " §8(" + kindLabel + "§8, "
                    + definition.rarityLabel() + "§8)");
            if (!definition.description().isBlank()) {
                sender.sendMessage("§8   " + definition.description());
            }
        }
        return true;
    }

    private String traitSummary(org.bukkit.entity.Entity entity) {
        StringBuilder names = new StringBuilder();
        for (TraitDefinition trait : traitManager.traitsOf(entity)) {
            if (names.length() > 0) {
                names.append("§7, §d");
            }
            names.append(trait.displayName());
        }
        return names.toString();
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("nexushatchlings.admin")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return true;
        }
        plugin.reloadConfig();
        config.load();
        traitRegistry.load();
        reloadHook.run();
        sender.sendMessage("§a[NexusHatchlings] §fConfig reloaded.");
        return true;
    }

    private String usage() {
        return "§cUsage: /nexushatchlings <give [player]|list|release [all]|traits|reload>";
    }
}
