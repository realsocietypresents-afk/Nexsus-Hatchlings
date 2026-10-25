package com.nexuscraft.nexushatchlings;

import org.bukkit.command.CommandSender;

/** The shared "how do hatchlings work" formatted message -- sent by {@code /nexushatchlings} on
 *  demand and, if {@code settings.first-join-message} is on, automatically once per player (see
 *  HatchlingFirstJoinListener). Kept in one place, same reasoning as NexusWards' WardMessages and
 *  NexusForge's GearMessages, so every call site can never drift apart. */
public final class HatchlingMessages {

    private HatchlingMessages() {
    }

    public static void sendOverview(CommandSender sender, HatchlingConfig config, TraitRegistry traitRegistry) {
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§d§lHatchling Crystals §8-§7 shrink and tame any mob");
        sender.sendMessage("§7Throw one at a creature -- baby dragons, baby skeletons, baby wardens,");
        sender.sendMessage("§7whatever you like -- and it shrinks down §8and§7 is tamed to you, both in");
        sender.sendMessage("§7the same instant.");
        sender.sendMessage(" ");
        sender.sendMessage("§6Get one §8- §7/nexushatchlings give"
                + (config.recipeEnabled() ? " §8, or craft one shapeless:" : " §8(crafting is currently disabled)"));
        if (config.recipeEnabled()) {
            sender.sendMessage("§8  §7Amethyst Shard §8+ §7Gold Ingot §8+ §7Slime Ball §8+ §7Ender Pearl");
        }
        sender.sendMessage("§6Throw it §8- §7right-click a mob-holding hand toward any creature.");
        sender.sendMessage("§8  Wherever it lands, whatever eligible mob wanders close gets tamed.");
        sender.sendMessage("§6Watch it grow §8- §7a straight climb from " + percent(config.startScale())
                + "§7% to " + percent(config.maxScale()) + "§7% size over " + trimDecimal(config.daysToGrow())
                + " real day(s), whether you're online to see it or not.");
        sender.sendMessage(" ");
        if (traitRegistry.enabled() && !traitRegistry.all().isEmpty()) {
            sender.sendMessage("§6Special attributes §8- §7every new hatchling rolls "
                    + traitRegistry.countMin() + "-" + traitRegistry.countMax() + " of "
                    + traitRegistry.all().size() + " possible traits the instant it's tamed --");
            sender.sendMessage("§8  stat boosts, real abilities, and cosmetic name flair. "
                    + "§7/nexushatchlings traits §8for the full catalog.");
            sender.sendMessage(" ");
        }
        sender.sendMessage("§8Commands: §7give [player] §8, §7list §8, §7release [all] §8, §7traits §8, §7reload");
        sender.sendMessage("§8§m                                        ");
    }

    private static String percent(double scale) {
        return trimDecimal(scale * 100.0);
    }

    private static String trimDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
