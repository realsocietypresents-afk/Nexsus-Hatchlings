package com.nexuscraft.nexushatchlings;

/** STAT scales one real Attribute (GENERIC_MAX_HEALTH, GENERIC_MOVEMENT_SPEED, GENERIC_ATTACK_DAMAGE) up, phased in with
 *  growth progress exactly like size already is. ABILITY grants a coded behavior (see
 *  {@link AbilityEffect}) -- these need real logic, not just a number, so unlike stats they're a
 *  fixed, small set rather than anything config can invent from scratch. COSMETIC changes nothing
 *  mechanical -- just a colored prefix baked into the hatchling's display name. */
public enum TraitKind {
    STAT,
    ABILITY,
    COSMETIC
}
