package com.nexuscraft.nexushatchlings;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads every {@link TraitDefinition} out of {@code config.yml}'s {@code traits:} list, plus the
 * {@code traits.enabled}/{@code count-min}/{@code count-max} settings that control how many roll
 * per hatchling. Same defensive stance every config parser in this plugin family takes: a
 * malformed entry (unknown kind/attribute/ability, missing id/display-name) is skipped
 * individually with a logged warning, never fatal to the rest of the list.
 */
public final class TraitRegistry {

    private final JavaPlugin plugin;
    private final Map<String, TraitDefinition> byId = new LinkedHashMap<>();
    private boolean enabled = true;
    private int countMin = 1;
    private int countMax = 2;

    public TraitRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("traits.enabled", true);
        this.countMin = Math.max(0, config.getInt("traits.count-min", 1));
        this.countMax = Math.max(countMin, config.getInt("traits.count-max", 2));

        byId.clear();
        for (Map<?, ?> raw : config.getMapList("traits.definitions")) {
            TraitDefinition definition = parseOne(raw);
            if (definition != null) {
                if (byId.containsKey(definition.id())) {
                    plugin.getLogger().warning("NexusHatchlings: duplicate trait id '" + definition.id()
                            + "' -- keeping the first one, skipping this later entry.");
                    continue;
                }
                byId.put(definition.id(), definition);
            }
        }
        plugin.getLogger().info("NexusHatchlings: loaded " + byId.size() + " possible traits ("
                + (enabled ? countMin + "-" + countMax + " roll per new hatchling)" : "rolling disabled)"));
    }

    private TraitDefinition parseOne(Map<?, ?> raw) {
        String id = str(raw, "id", null);
        String displayName = str(raw, "display-name", null);
        if (id == null || id.isBlank() || displayName == null || displayName.isBlank()) {
            plugin.getLogger().warning("NexusHatchlings: skipping a trait entry missing 'id' or 'display-name'.");
            return null;
        }

        TraitKind kind;
        try {
            kind = TraitKind.valueOf(str(raw, "kind", "").toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "NexusHatchlings: trait '{0}'' has an unknown or missing ''kind'' -- skipping it entirely.", id);
            return null;
        }

        String description = str(raw, "description", "");
        int rarityWeight = intVal(raw, "rarity-weight", 20);
        if (rarityWeight < 1) {
            rarityWeight = 1;
        }

        Attribute statAttribute = null;
        double statMultiplier = 1.0;
        AbilityEffect abilityEffect = null;
        String cosmeticPrefix = "";

        switch (kind) {
            case STAT -> {
                try {
                    statAttribute = Attribute.valueOf(str(raw, "attribute", "").toUpperCase());
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("NexusHatchlings: STAT trait '" + id
                            + "' has an unknown or missing 'attribute' -- skipping it entirely.");
                    return null;
                }
                statMultiplier = doubleVal(raw, "multiplier", 1.0);
                if (statMultiplier <= 0) {
                    plugin.getLogger().warning("NexusHatchlings: STAT trait '" + id
                            + "' has a non-positive multiplier -- defaulting to 1.5.");
                    statMultiplier = 1.5;
                }
            }
            case ABILITY -> {
                try {
                    abilityEffect = AbilityEffect.valueOf(str(raw, "ability", "").toUpperCase());
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("NexusHatchlings: ABILITY trait '" + id
                            + "' has an unknown or missing 'ability' -- skipping it entirely.");
                    return null;
                }
            }
            case COSMETIC -> {
                cosmeticPrefix = str(raw, "prefix", "");
                if (cosmeticPrefix.isBlank()) {
                    plugin.getLogger().warning("NexusHatchlings: COSMETIC trait '" + id
                            + "' has no 'prefix' -- skipping it entirely (a cosmetic with nothing to show is pointless).");
                    return null;
                }
            }
        }

        return new TraitDefinition(id, displayName, description, kind, rarityWeight,
                statAttribute, statMultiplier, abilityEffect, cosmeticPrefix);
    }

    public boolean enabled() {
        return enabled;
    }

    public int countMin() {
        return countMin;
    }

    public int countMax() {
        return countMax;
    }

    public TraitDefinition get(String id) {
        return byId.get(id);
    }

    public Map<String, TraitDefinition> all() {
        return byId;
    }

    private static String str(Map<?, ?> raw, String key, String def) {
        Object value = raw.get(key);
        return value == null ? def : String.valueOf(value);
    }

    private static int intVal(Map<?, ?> raw, String key, int def) {
        Object value = raw.get(key);
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static double doubleVal(Map<?, ?> raw, String key, double def) {
        Object value = raw.get(key);
        if (value == null) {
            return def;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
