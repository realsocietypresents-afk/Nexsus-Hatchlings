package com.nexuscraft.nexushatchlings;

import org.bukkit.attribute.Attribute;

/**
 * One possible trait a hatchling can roll at taming time -- config-driven, {@code traits:} in
 * config.yml. Only the fields that matter for its {@link TraitKind} are actually used: a STAT
 * trait reads {@code statAttribute}/{@code statMultiplier} and ignores the ability/cosmetic fields,
 * an ABILITY trait reads only {@code abilityEffect}, a COSMETIC trait reads only
 * {@code cosmeticPrefix} -- same "not every field applies to every category" shape as
 * NexusForge's GearDefinition (sharpenCost only meaningful for WEAPON there).
 */
public record TraitDefinition(
        String id,
        String displayName,
        String description,
        TraitKind kind,
        int rarityWeight,
        Attribute statAttribute,
        double statMultiplier,
        AbilityEffect abilityEffect,
        String cosmeticPrefix
) {
    /** Flavor label derived from rarityWeight, purely for display -- higher weight rolls more
     *  often, so a low weight reads as the rarer trait. Thresholds are arbitrary but consistent. */
    public String rarityLabel() {
        if (rarityWeight >= 35) return "§fCommon";
        if (rarityWeight >= 18) return "§aUncommon";
        if (rarityWeight >= 8) return "§9Rare";
        return "§6§lLegendary";
    }
}
