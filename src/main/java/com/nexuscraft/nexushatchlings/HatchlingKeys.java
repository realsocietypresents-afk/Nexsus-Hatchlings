package com.nexuscraft.nexushatchlings;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Every PersistentDataContainer key this plugin reads or writes, in one place. Everything here is
 * PDC-only -- there is no separate database or data file. A tamed hatchling carries its own owner
 * and age directly on itself (so it survives chunk unloads, world saves, and restarts exactly like
 * any other entity data does), and a waiting crystal item carries its own marker and drop time the
 * same way.
 */
public final class HatchlingKeys {

    public final NamespacedKey crystalMarker;
    public final NamespacedKey droppedAt;
    public final NamespacedKey thrownBy;
    public final NamespacedKey owner;
    public final NamespacedKey tamedAt;
    public final NamespacedKey milestone;
    // Trait-related. traits is a comma-joined list of TraitDefinition ids rolled once at taming
    // time (see TraitManager#rollAndStore) -- never re-rolled. The three baseXxx keys anchor the
    // pre-trait vanilla value of each attribute a STAT trait might touch, captured once so repeated
    // reapply cycles compute the scaled-by-growth target from a fixed anchor instead of compounding
    // on top of whatever the previous cycle already set (see TraitManager#applyEffects's own doc
    // comment for why that matters).
    public final NamespacedKey traits;
    public final NamespacedKey baseMaxHealth;
    public final NamespacedKey baseMovementSpeed;
    public final NamespacedKey baseAttackDamage;
    // Dragon-related: a marker so only dragons this plugin set up are managed, plus the colour id and the command word.
    public final NamespacedKey dragonMark;
    public final NamespacedKey dragonColor;
    public final NamespacedKey dragonWord;
    /** Where a dragon told to stay waits: "world|x|y|z". */
    public final NamespacedKey dragonStay;
    /** NexusBestiary's "this pet was told to wait where it is" note (plain text key, so the plugin need not be installed). */
    public final NamespacedKey bestiaryHeld;

    public HatchlingKeys(JavaPlugin plugin) {
        this.crystalMarker = new NamespacedKey(plugin, "hatchling-crystal");
        this.droppedAt = new NamespacedKey(plugin, "crystal-dropped-at");
        this.thrownBy = new NamespacedKey(plugin, "crystal-thrown-by");
        this.owner = new NamespacedKey(plugin, "hatchling-owner");
        this.tamedAt = new NamespacedKey(plugin, "hatchling-tamed-at");
        this.milestone = new NamespacedKey(plugin, "hatchling-milestone");
        this.traits = new NamespacedKey(plugin, "hatchling-traits");
        this.baseMaxHealth = new NamespacedKey(plugin, "trait-base-max-health");
        this.baseMovementSpeed = new NamespacedKey(plugin, "trait-base-movement-speed");
        this.baseAttackDamage = new NamespacedKey(plugin, "trait-base-attack-damage");
        this.dragonMark = new NamespacedKey(plugin, "dragon-mark");
        this.dragonColor = new NamespacedKey(plugin, "dragon-color");
        this.dragonWord = new NamespacedKey(plugin, "dragon-word");
        this.dragonStay = new NamespacedKey(plugin, "dragon-stay");
        this.bestiaryHeld = NamespacedKey.fromString("nexusbestiary:held");
    }

    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
    public static final PersistentDataType<Long, Long> LONG = PersistentDataType.LONG;
    public static final PersistentDataType<Integer, Integer> INTEGER = PersistentDataType.INTEGER;
    public static final PersistentDataType<Double, Double> DOUBLE = PersistentDataType.DOUBLE;
}
