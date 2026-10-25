package com.nexuscraft.nexushatchlings;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rolls, stores, and reapplies each hatchling's traits. Rolled exactly once, at taming
 * ({@link #rollAndStore}) -- never re-rolled by a reapply cycle, a reload, or anything else.
 * Storage is the same "the entity is the record" philosophy as the rest of this plugin: trait ids
 * live as a single comma-joined PersistentDataContainer string directly on the mob.
 */
public final class TraitManager {

    private final TraitRegistry registry;
    private final HatchlingKeys keys;
    private final HatchlingConfig config;
    private final Random random = new Random();

    public TraitManager(TraitRegistry registry, HatchlingKeys keys, HatchlingConfig config) {
        this.registry = registry;
        this.keys = keys;
        this.config = config;
    }

    public List<TraitDefinition> traitsOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(keys.traits, HatchlingKeys.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<TraitDefinition> result = new ArrayList<>();
        for (String id : raw.split(",")) {
            TraitDefinition definition = registry.get(id.trim());
            if (definition != null) {
                result.add(definition);
            }
        }
        return result;
    }

    public boolean hasAbility(Entity entity, AbilityEffect effect) {
        for (TraitDefinition trait : traitsOf(entity)) {
            if (trait.kind() == TraitKind.ABILITY && trait.abilityEffect() == effect) {
                return true;
            }
        }
        return false;
    }

    /** Rolls {@code traits.count-min}..{@code count-max} traits (weighted by rarity-weight, never
     *  the same trait twice on one hatchling), stores them, anchors any STAT trait's pre-trait
     *  attribute value, and bakes any COSMETIC prefix into the hatchling's display name. Called
     *  exactly once, from {@link HatchlingManager#tame}. */
    public List<TraitDefinition> rollAndStore(Mob mob) {
        if (!registry.enabled() || registry.all().isEmpty()) {
            return List.of();
        }

        int min = registry.countMin();
        int max = registry.countMax();
        int count = min == max ? min : min + random.nextInt(max - min + 1);

        List<TraitDefinition> pool = new ArrayList<>(registry.all().values());
        List<TraitDefinition> rolled = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            TraitDefinition pick = weightedPick(pool);
            pool.remove(pick);
            rolled.add(pick);
        }
        if (rolled.isEmpty()) {
            return rolled;
        }

        StringBuilder ids = new StringBuilder();
        for (TraitDefinition trait : rolled) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(trait.id());
            if (trait.kind() == TraitKind.STAT) {
                anchorBaseValue(mob, trait.statAttribute());
            }
        }
        mob.getPersistentDataContainer().set(keys.traits, HatchlingKeys.STRING, ids.toString());
        applyCosmeticName(mob, rolled);
        return rolled;
    }

    private TraitDefinition weightedPick(List<TraitDefinition> pool) {
        int total = 0;
        for (TraitDefinition definition : pool) {
            total += definition.rarityWeight();
        }
        int roll = random.nextInt(Math.max(1, total));
        int cumulative = 0;
        for (TraitDefinition definition : pool) {
            cumulative += definition.rarityWeight();
            if (roll < cumulative) {
                return definition;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private void anchorBaseValue(Mob mob, Attribute attribute) {
        NamespacedKey key = baseKeyFor(attribute);
        if (key == null || mob.getPersistentDataContainer().has(key, HatchlingKeys.DOUBLE)) {
            return;
        }
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            mob.getPersistentDataContainer().set(key, HatchlingKeys.DOUBLE, instance.getBaseValue());
        }
    }

    private NamespacedKey baseKeyFor(Attribute attribute) {
        if (attribute == Attribute.GENERIC_MAX_HEALTH) {
            return keys.baseMaxHealth;
        }
        if (attribute == Attribute.GENERIC_MOVEMENT_SPEED) {
            return keys.baseMovementSpeed;
        }
        if (attribute == Attribute.GENERIC_ATTACK_DAMAGE) {
            return keys.baseAttackDamage;
        }
        return null;
    }

    /**
     * Called every reapply cycle, same cadence as growth/follow ({@code settings.reapply-interval-minutes}).
     * A STAT trait's bonus phases in with growth progress -- 0% extra at the moment of taming, the
     * full {@code multiplier} once fully grown -- computed fresh each cycle from the anchored
     * pre-trait base value, never by multiplying the *current* (possibly already-boosted) value.
     * That anchor is what stops the bonus from compounding further every single reapply cycle
     * forever, which a naive "base *= multiplier" would do.
     */
    public void applyEffects(Mob mob, double growthFraction) {
        List<TraitDefinition> traits = traitsOf(mob);
        if (traits.isEmpty()) {
            return;
        }
        int durationTicks = 20 * 60 * (Math.max(1, config.reapplyIntervalMinutes()) + 1);
        for (TraitDefinition trait : traits) {
            switch (trait.kind()) {
                case STAT -> applyStat(mob, trait, growthFraction);
                case ABILITY -> applyAbility(mob, trait, durationTicks);
                case COSMETIC -> {
                    // Baked into the display name once, at taming -- nothing to reapply.
                }
            }
        }
    }

    private void applyStat(Mob mob, TraitDefinition trait, double growthFraction) {
        NamespacedKey key = baseKeyFor(trait.statAttribute());
        if (key == null) {
            return;
        }
        Double base = mob.getPersistentDataContainer().get(key, HatchlingKeys.DOUBLE);
        AttributeInstance instance = mob.getAttribute(trait.statAttribute());
        if (base == null || instance == null) {
            return;
        }
        double bonusFactor = 1.0 + (trait.statMultiplier() - 1.0) * growthFraction;
        instance.setBaseValue(base * bonusFactor);
    }

    private void applyAbility(Mob mob, TraitDefinition trait, int durationTicks) {
        switch (trait.abilityEffect()) {
            case REGENERATIVE -> mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0));
            case SWIFT -> mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0));
            case KEEN_EYED -> mob.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, durationTicks, 0));
            case FIRE_IMMUNE -> {
                // Nothing to reapply -- handled directly by TraitAbilityListener cancelling the
                // relevant damage cause whenever it happens, not by anything periodic.
            }
        }
    }

    private void applyCosmeticName(Mob mob, List<TraitDefinition> rolled) {
        StringBuilder prefix = new StringBuilder();
        for (TraitDefinition trait : rolled) {
            if (trait.kind() == TraitKind.COSMETIC) {
                prefix.append(trait.cosmeticPrefix());
            }
        }
        String species = prettyName(mob.getType().name());
        String name = prefix.length() > 0 ? prefix + "§r " + species : species;
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }

    /** Clears which traits a hatchling has -- called on release. Deliberately does NOT try to
     *  revert an already-phased-in STAT bonus back to the anchored base value: a released hatchling
     *  keeps whatever size and whatever stat growth it's already earned, the same "keeps whatever
     *  size it's grown to" philosophy {@link HatchlingManager#release} already uses for scale. */
    public void clear(Entity entity) {
        entity.getPersistentDataContainer().remove(keys.traits);
    }

    private static String prettyName(String rawEnumName) {
        String[] words = rawEnumName.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return out.toString();
    }
}
