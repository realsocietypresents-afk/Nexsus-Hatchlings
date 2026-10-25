package com.nexuscraft.nexushatchlings;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** The one ABILITY trait that isn't a periodic potion effect: FIRE_IMMUNE cancels fire/fire-tick/
 *  lava damage outright for any tamed hatchling that rolled it. Everything else can still hurt it
 *  normally (or not, per {@code settings.invulnerable-while-growing} -- see HatchlingListener,
 *  which this deliberately doesn't duplicate or interact with; both listeners independently decide
 *  whether to cancel the same event, and either one cancelling is enough). */
public final class TraitAbilityListener implements Listener {

    private final TraitManager traits;

    public TraitAbilityListener(TraitManager traits) {
        this.traits = traits;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.LAVA) {
            return;
        }
        Entity entity = event.getEntity();
        if (traits.hasAbility(entity, AbilityEffect.FIRE_IMMUNE)) {
            event.setCancelled(true);
        }
    }
}
