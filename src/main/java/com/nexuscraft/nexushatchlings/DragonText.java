package com.nexuscraft.nexushatchlings;

import java.util.List;

/** The words the dragon system says to players, in one place. */
public final class DragonText {

    private DragonText() {
    }

    public static final String PREFIX = "§d[Dragons] §f";

    public static String joinNames(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** Why an attack order did not start. */
    public static String orderFailure(DragonService.OrderResult r, String word, String named) {
        return switch (r.status()) {
            case STARTED -> "";
            case DISABLED -> PREFIX + "Dragons are not allowed to attack on this server.";
            case NOT_OWNER -> PREFIX + "That dragon is not yours.";
            case NOT_ADULT -> PREFIX + "Your dragon is not fully grown yet. It cannot fight until it is.";
            case BUSY -> PREFIX + "Your dragon is carrying a rider and cannot be sent anywhere.";
            case WRONG_WORLD -> PREFIX + "Your dragon is in another world and cannot hear you.";
            case FAR_AWAY -> PREFIX + "Your dragon is too far away to hear you.";
            case NO_TARGET -> switch (r.reason()) {
                case NOT_FOUND -> PREFIX + "There is nobody called " + (named == null ? "that" : named) + " close enough to see.";
                case TOO_FAR -> PREFIX + (named == null ? "That player" : named) + " is too far from your dragon.";
                case OWNER -> PREFIX + "Your dragon will not go after you.";
                case ALLY -> PREFIX + (named == null ? "That player" : named) + " stands with you, and your dragon will not touch them.";
                case CANNOT_BE_HURT -> PREFIX + (named == null ? "That player" : named) + " cannot be hurt right now.";
                default -> PREFIX + "There is nobody near your dragon to go after.";
            };
        };
    }

    public static String mountFailure(DragonService.MountResult r) {
        return switch (r) {
            case OK -> "";
            case DISABLED -> PREFIX + "Riding dragons is switched off on this server.";
            case WILD -> PREFIX + "This dragon is wild. Tame it with a Hatchling Crystal first.";
            case NOT_OWNER -> PREFIX + "This dragon is not yours to ride.";
            case TOO_YOUNG -> PREFIX + "It is too young to carry you. It has to be able to fly first.";
            case OCCUPIED -> PREFIX + "Somebody is already riding it.";
        };
    }

    /** What a stay order answers. */
    public static String stayMessage(DragonService.StayResult r, boolean atPlayer) {
        return switch (r) {
            case OK -> PREFIX + (atPlayer ? "Your dragon comes to where you stand and waits there. /dragon follow brings it back."
                    : "Your dragon waits right where it is. /dragon follow brings it back.");
            case NOT_OWNER -> PREFIX + "That dragon is not yours.";
            case RIDDEN -> PREFIX + "Somebody is riding it. Get off first.";
            case WRONG_WORLD -> PREFIX + "Your dragon is in another world, so it cannot come to where you stand.";
        };
    }

    /** What a change of command word answers. */
    public static String wordMessage(DragonService.WordResult r, String word) {
        return switch (r) {
            case OK -> PREFIX + "Your dragon answers to §f" + CommandWords.normalize(word) + "§f now.";
            case INVALID -> PREFIX + "A word is " + CommandWords.MIN_LENGTH + " to " + CommandWords.MAX_LENGTH
                    + " letters, nothing else, and not one of: " + String.join(", ", new java.util.TreeSet<>(CommandWords.RESERVED)) + ".";
            case TAKEN -> PREFIX + "Another dragon of yours already has that word.";
            case NOT_OWNER -> PREFIX + "That dragon is not yours.";
        };
    }

    public static List<String> help(boolean admin) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("§8§m                                        ");
        lines.add("§d§lDragons");
        lines.add("§7Put a Dragon Egg on a lit campfire and wait. The baby is wild: throw a Hatchling Crystal at it to tame it.");
        lines.add("§7Right-click a dragon with a dye to change its colour. It walks while small, flies when grown enough to carry you");
        lines.add("§7(right-click it to climb on, right-click in the air to start or stop flying, sneak to get off), and fights once fully grown.");
        lines.add(" ");
        lines.add("§6/dragon <word> §7- your dragon attacks the nearest player close to it");
        lines.add("§6/dragon <word> <player> §7- it goes after that player");
        lines.add("§6/dragon <word> all §7- it goes after every player near it");
        lines.add("§6/dragon stop [word] §7- call it off");
        lines.add("§6/dragon menu §7- a menu with a button for each dragon: orders, colour, word, letting one go");
        lines.add("§6/dragon stay <word|all> [here] §7- it waits where it is, or (with here) where you stand");
        lines.add("§6/dragon follow <word|all> §7- it follows you again");
        lines.add("§6/dragon release <word> confirm §7- let a dragon go (it turns wild)");
        lines.add("§6/dragon list §7- your dragons and their words");
        lines.add("§6/dragon word <word> <new word> §7- give a dragon a different word (3-16 letters)");
        lines.add("§7You can keep up to the limit set on this server (ten by default). Dragons following you spread out around you instead of piling up.");
        lines.add("§7Your own dragons and anyone allied with you in NexusHouses are never attacked.");
        if (admin) {
            lines.add("§6/dragon color <word> <colour> §7- change a colour (staff)");
            lines.add("§6/dragon egg give [player] | hatch | spawn §7- staff tools");
        }
        lines.add("§8§m                                        ");
        return lines;
    }
}
