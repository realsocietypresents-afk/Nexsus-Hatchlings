package com.nexuscraft.nexushatchlings;

/**
 * The fixed, small set of real behaviors an ABILITY trait can grant -- unlike a STAT trait (which
 * is just "which Attribute, what multiplier" and can be entirely config-defined), an ability needs
 * actual code behind it, so this is a closed set rather than something config can invent from
 * scratch. Config still picks *which* of these a given trait id grants, and can add/remove/reweight
 * trait entries freely -- it just can't define a brand-new kind of ability without a code change.
 */
public enum AbilityEffect {
    /** Immune to fire, fire-tick, and lava damage specifically (see TraitAbilityListener) --
     *  everything else can still hurt it normally. */
    FIRE_IMMUNE,
    /** A steady trickle of real vanilla Regeneration, reapplied every reapply cycle so it never
     *  actually runs out between applies. */
    REGENERATIVE,
    /** A steady trickle of real vanilla Speed, same reapply pattern as REGENERATIVE. */
    SWIFT,
    /** A steady trickle of real vanilla Night Vision on the hatchling itself (not an aura on its
     *  owner -- kept simple for v1), same reapply pattern. */
    KEEN_EYED
}
