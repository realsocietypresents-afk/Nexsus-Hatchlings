package com.nexuscraft.nexushatchlings;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** The word an owner types to give a dragon an order: letters only, three to sixteen of them, never one of the plugin's own sub-commands. */
public final class CommandWords {

    private CommandWords() {
    }

    /** Words that already mean something to /dragon. */
    public static final Set<String> RESERVED = Set.of("help", "list", "word", "color", "colour", "attack", "stop", "egg", "all", "info", "ride", "give",
            "stay", "follow", "come", "menu", "release");

    /** Invented words used as the first name of a dragon. */
    public static final List<String> DEFAULTS = List.of(
            "vaelrix", "ashkarra", "syrrath", "korvael", "emberyn", "thalvor", "drazael", "nyxara", "orrukai", "zephyra",
            "myrrhal", "kaelith", "ostravel", "velkorin", "rhazura", "tyrmaal", "ignarys", "solvaris", "umbraxa", "pyrranth");

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;

    /** Lower case, trimmed; null stays null. */
    public static String normalize(String word) {
        return word == null ? null : word.trim().toLowerCase(Locale.ROOT);
    }

    /** True if this can be a command word. */
    public static boolean valid(String word) {
        String w = normalize(word);
        if (w == null || w.length() < MIN_LENGTH || w.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return !RESERVED.contains(w);
    }

    /** A valid word that is not in {@code taken} (all lower case). Falls back to a default with letters added if every default is used. */
    public static String pickDefault(Set<String> taken, Random random) {
        List<String> free = DEFAULTS.stream().filter(w -> !taken.contains(w)).toList();
        if (!free.isEmpty()) {
            return free.get(random.nextInt(free.size()));
        }
        String base = DEFAULTS.get(random.nextInt(DEFAULTS.size()));
        StringBuilder word = new StringBuilder(base);
        while (taken.contains(word.toString()) || word.length() > MAX_LENGTH) {
            if (word.length() >= MAX_LENGTH) {
                word = new StringBuilder(DEFAULTS.get(random.nextInt(DEFAULTS.size())));
            }
            word.append((char) ('a' + random.nextInt(26)));
        }
        return word.toString();
    }
}
