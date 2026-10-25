package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexushatchlings.Kit.expect;

/** A crystal reaching a mob: direct hits, waiting crystals, refusals, Endermen, the hand it is thrown from, and owners milking their own animals. */
public class CrystalTest {

    public static void main(String[] args) {
        directHit();
        enderman();
        refusals();
        refusalDelivery();
        landing();
        scanning();
        dragonPart();
        throwing();
        tameSurvivesTraitTrouble();
        endermanRules();
        selfTeleport();
        heldPets();
        ownerFarming();
        protection();
        details();
        config();
        Kit.finish();
    }

    /** Everything a crystal needs, wired the way the plugin wires it. */
    static final class Rig {
        final Env e = new Env();
        final CrystalItem crystal = new CrystalItem(e.keys);
        final CrystalTaming taming = new CrystalTaming(e.hatch, e.manager, crystal);
        final CrystalImpactListener impact = new CrystalImpactListener(e.keys, e.manager, taming);
        final CrystalScanTask scan = new CrystalScanTask(e.plugin, e.hatch, e.keys, e.manager, taming);

        /** A crystal in flight: a tagged snowball thrown by this player and sitting where it is about to land. */
        Snowball flying(Kit.TestPlayer by, Location at) {
            Snowball s = by.launchProjectile(Snowball.class);
            s.stubSetLocation(at);
            s.getPersistentDataContainer().set(e.keys.crystalMarker, HatchlingKeys.STRING, "true");
            s.getPersistentDataContainer().set(e.keys.thrownBy, HatchlingKeys.STRING, by.getUniqueId().toString());
            return s;
        }

        void hit(Snowball s, Entity entity) {
            impact.onProjectileHit(new ProjectileHitEvent(s, entity, null));
        }

        <T extends Mob> T spawn(Class<T> type, double x, double y, double z) {
            return e.world.spawn(e.at(x, y, z), type);
        }

        List<Item> items() {
            List<Item> out = new ArrayList<>();
            for (Entity en : e.world.getEntities()) {
                if (en instanceof Item it) {
                    out.add(it);
                }
            }
            return out;
        }

        int crystalsIn(Kit.TestPlayer p) {
            int n = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (st != null && crystal.isCrystal(st)) {
                    n += st.getAmount();
                }
            }
            return n;
        }
    }

    static void directHit() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        // the mob that was hit is far from where the snowball ends up, and a decoy stands right at that spot
        Zombie hitMob = r.spawn(Zombie.class, 30, 64, 30);
        Zombie decoy = r.spawn(Zombie.class, 1, 64, 1);
        Snowball s = r.flying(p, r.e.at(2, 64, 2));
        r.hit(s, hitMob);
        expect(r.e.manager.isTame(hitMob) && p.getUniqueId().equals(r.e.manager.ownerOf(hitMob)), "the mob that was hit is tamed to the thrower");
        expect(!r.e.manager.isTame(decoy), "and a mob that merely stands where the snowball ended up is not");
        expect(r.items().isEmpty(), "a direct hit leaves no crystal waiting on the ground");
        expect(!s.isValid(), "the snowball is used up");
        expect(p.heard("Tamed a zombie at 30, 64, 30"), "the owner is told what was tamed and where: " + p.messages);
        expect(hitMob.getPersistentDataContainer().has(r.e.keys.tamedAt, HatchlingKeys.LONG), "its growth clock is running");
        r.scan.scanOnce();
        expect(!r.e.manager.isTame(decoy), "a later scan has no crystal to act on");

        // a second crystal at the same mob does not tame it twice
        Snowball again = r.flying(p, r.e.at(30, 64, 30));
        p.messages.clear();
        r.hit(again, hitMob);
        expect(p.heard("already your hatchling"), "hitting your own hatchling says so: " + p.messages);
    }

    static void enderman() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Enderman en = r.spawn(Enderman.class, 8, 64, 8);
        expect(r.e.manager.isEligible(en), "an Enderman is a valid target");
        // by the time a scan would run the Enderman has teleported; the direct hit does not care
        Snowball s = r.flying(p, r.e.at(8, 64, 8));
        r.hit(s, en);
        expect(r.e.manager.isTame(en), "a crystal that hits an Enderman tames it");
        expect(p.heard("Tamed a enderman"), "and says so: " + p.messages);
        expect(!r.e.hatch.isExcluded(en.getType()), "nothing excludes Endermen by default");

        Mob far = r.spawn(Enderman.class, 40, 64, 40);
        Snowball miss = r.flying(p, r.e.at(8, 64, 8));
        r.hit(miss, null);
        r.scan.scanOnce();
        expect(!r.e.manager.isTame(far), "a waiting crystal does not reach an Enderman that is out of range");
    }

    static void refusals() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Kit.TestPlayer other = r.e.player("Other", 50, 64, 50);

        Zombie theirs = r.spawn(Zombie.class, 5, 64, 5);
        r.e.manager.tame(theirs, other.getUniqueId());
        Zombie bystander = r.spawn(Zombie.class, 6, 64, 5);
        Snowball s = r.flying(p, r.e.at(5, 64, 5));
        r.hit(s, theirs);
        expect(other.getUniqueId().equals(r.e.manager.ownerOf(theirs)), "a crystal does not take someone else's hatchling");
        expect(!r.e.manager.isTame(bystander), "and does not tame the mob standing next to it instead");
        expect(p.heard("already belongs to someone else") && p.heard("came back"), "the thrower is told why: " + p.messages);
        expect(r.crystalsIn(p) == 1, "and gets the crystal back");
        expect(r.items().isEmpty(), "nothing is left waiting on the ground");

        // a type switched off in the config
        r.e.plugin.getConfig().set("excluded-mob-types", List.of("ENDERMAN"));
        r.e.hatch.load();
        Enderman banned = r.spawn(Enderman.class, 9, 64, 9);
        p.messages.clear();
        r.hit(r.flying(p, r.e.at(9, 64, 9)), banned);
        expect(!r.e.manager.isTame(banned), "a type the config excludes is not tamed");
        expect(p.heard("can't be tamed here"), "and the thrower is told: " + p.messages);
        expect(r.crystalsIn(p) == 2, "and gets the crystal back again");
    }

    static void refusalDelivery() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Kit.TestPlayer other = r.e.player("Other", 50, 64, 50);
        Zombie theirs = r.spawn(Zombie.class, 5, 64, 5);
        r.e.manager.tame(theirs, other.getUniqueId());

        // a full inventory: the crystal is dropped at the thrower's feet
        for (int i = 0; i < 54; i++) {
            p.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        }
        int dropsBefore = r.e.world.stubDrops.size();
        r.hit(r.flying(p, r.e.at(5, 64, 5)), theirs);
        expect(r.e.world.stubDrops.size() == dropsBefore + 1 && r.crystal.isCrystal(r.e.world.stubDrops.get(dropsBefore)),
                "with a full inventory the crystal drops instead of vanishing");

        // a thrower who has left: the crystal drops where it landed
        Kit.TestPlayer gone = r.e.player("Gone", 1, 64, 1);
        Snowball s = r.flying(gone, r.e.at(5, 64, 5));
        gone.online = false;
        int before = r.e.world.stubDrops.size();
        r.hit(s, theirs);
        expect(r.e.world.stubDrops.size() == before + 1, "a thrower who logged off still gets the crystal back, on the ground");
        expect(gone.messages.isEmpty(), "and is not messaged");
    }

    static void landing() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Snowball s = r.flying(p, r.e.at(10, 64, 10));
        r.hit(s, null);
        expect(!s.isValid(), "a crystal that hits a block is used up");
        List<Item> waiting = r.items();
        expect(waiting.size() == 1, "and leaves one waiting item");
        Item it = waiting.get(0);
        expect(it.isUnlimitedLifetime(), "that does not despawn on its own");
        expect(it.getPersistentDataContainer().has(r.e.keys.crystalMarker, HatchlingKeys.STRING), "it is marked as a crystal");
        expect(p.getUniqueId().toString().equals(it.getPersistentDataContainer().get(r.e.keys.thrownBy, HatchlingKeys.STRING)),
                "and remembers who threw it");

        // hitting something that is not a mob behaves like landing
        Kit.TestPlayer other = r.e.player("Other", 20, 64, 20);
        Snowball t = r.flying(p, r.e.at(20, 64, 20));
        r.hit(t, other);
        expect(r.items().size() == 2 && !t.isValid(), "a crystal that hits a player lands like any other");

        // not a crystal, not a snowball, or no thrower
        Snowball plain = p.launchProjectile(Snowball.class);
        plain.stubSetLocation(r.e.at(1, 64, 1));
        r.hit(plain, null);
        expect(plain.isValid() && r.items().size() == 2, "an ordinary snowball is left alone");
        Snowball noPlace = r.flying(p, null);
        noPlace.stubSetLocation(null);
        r.hit(noPlace, null);
        expect(noPlace.isValid(), "a crystal with no place is left alone");
        Snowball anon = r.flying(p, r.e.at(3, 64, 3));
        anon.getPersistentDataContainer().remove(r.e.keys.thrownBy);
        Zombie z = r.spawn(Zombie.class, 3, 64, 3);
        r.hit(anon, z);
        expect(!r.e.manager.isTame(z), "a hit with nobody to tame it for tames nothing");
        expect(r.items().size() == 3, "and leaves the crystal to expire on the ground");
        Snowball junk = r.flying(p, r.e.at(4, 64, 4));
        junk.getPersistentDataContainer().set(r.e.keys.thrownBy, HatchlingKeys.STRING, "not-a-uuid");
        Zombie z2 = r.spawn(Zombie.class, 4, 64, 4);
        r.hit(junk, z2);
        expect(!r.e.manager.isTame(z2), "a damaged owner id tames nothing either");
    }

    static void scanning() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        // two mobs near the crystal, the farther one first in the world's list
        Zombie farther = r.spawn(Zombie.class, 12, 64, 10);
        Zombie nearer = r.spawn(Zombie.class, 10, 64, 11);
        r.hit(r.flying(p, r.e.at(10, 64, 10)), null);
        r.scan.scanOnce();
        expect(r.e.manager.isTame(nearer) && !r.e.manager.isTame(farther), "a waiting crystal tames the nearest mob");
        expect(r.items().isEmpty(), "and is used up");
        expect(p.heard("Tamed a zombie"), "the owner is told: " + p.messages);

        // nothing close: it waits
        r.hit(r.flying(p, r.e.at(100, 64, 100)), null);
        r.scan.scanOnce();
        expect(r.items().size() == 1, "with no mob close by the crystal keeps waiting");
        // a tamed mob, a player and an item are not targets
        Zombie taken = r.spawn(Zombie.class, 101, 64, 100);
        r.e.manager.tame(taken, p.getUniqueId());
        r.scan.scanOnce();
        expect(r.items().size() == 1, "an already-tamed mob is not a target");

        // it expires
        Item waiting = r.items().get(0);
        waiting.getPersistentDataContainer().set(r.e.keys.droppedAt, HatchlingKeys.LONG,
                System.currentTimeMillis() - (r.e.hatch.crystalExpirySeconds() + 5) * 1000L);
        r.scan.scanOnce();
        expect(r.items().isEmpty(), "an old crystal vanishes");

        // no recoverable owner: it is left alone
        Item orphan = r.e.world.dropItem(r.e.at(60, 64, 60), r.crystal.create());
        orphan.getPersistentDataContainer().set(r.e.keys.crystalMarker, HatchlingKeys.STRING, "true");
        orphan.getPersistentDataContainer().set(r.e.keys.droppedAt, HatchlingKeys.LONG, System.currentTimeMillis());
        Zombie close = r.spawn(Zombie.class, 60, 64, 61);
        r.scan.scanOnce();
        expect(!r.e.manager.isTame(close) && orphan.isValid(), "a crystal with no thrower tames nothing");
        orphan.getPersistentDataContainer().set(r.e.keys.thrownBy, HatchlingKeys.STRING, "garbage");
        r.scan.scanOnce();
        expect(!r.e.manager.isTame(close) && orphan.isValid(), "nor does one with a damaged thrower id");
        orphan.getPersistentDataContainer().set(r.e.keys.thrownBy, HatchlingKeys.STRING, p.getUniqueId().toString());
        r.scan.scanOnce();
        expect(r.e.manager.isTame(close) && !orphan.isValid(), "but once it has one it works");

        // an ordinary dropped item is ignored
        Item plain = r.e.world.dropItem(r.e.at(80, 64, 80), new ItemStack(Material.STONE));
        Zombie by = r.spawn(Zombie.class, 80, 64, 81);
        r.scan.scanOnce();
        expect(!r.e.manager.isTame(by) && plain.isValid(), "an ordinary item is not a crystal");

        // an owner who is offline still gets the animal, just no message
        Kit.TestPlayer away = r.e.player("Away", 200, 64, 200);
        Zombie late = r.spawn(Zombie.class, 200, 64, 201);
        r.hit(r.flying(away, r.e.at(200, 64, 200)), null);
        away.online = false;
        r.scan.scanOnce();
        expect(r.e.manager.isTame(late) && away.messages.isEmpty(), "taming for an owner who has logged off works silently");
    }

    static void dragonPart() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        EnderDragon d = r.e.baby(20, 64, 20);
        EnderDragonPart part = new EnderDragonPart(d);
        part.stubSetLocation(r.e.at(21, 64, 20));
        r.hit(r.flying(p, r.e.at(21, 64, 20)), part);
        expect(r.e.manager.isTame(d) && p.getUniqueId().equals(r.e.manager.ownerOf(d)), "a crystal that hits a dragon's body tames the dragon");
        expect(p.heard("Tamed a ender dragon"), "and says so: " + p.messages);
    }

    static ItemStack crystalIn(Rig r, int n) {
        ItemStack st = r.crystal.create();
        st.setAmount(n);
        return st;
    }

    static void throwing() {
        Rig r = new Rig();
        CrystalThrowListener l = new CrystalThrowListener(r.e.keys, r.crystal);
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);

        // main hand, one crystal
        ItemStack main = crystalIn(r, 1);
        p.getInventory().setItemInMainHand(main);
        l.onInteract(new PlayerInteractEvent(p, Action.RIGHT_CLICK_AIR, main, null));
        expect(p.getInventory().getItemInMainHand() == null, "a thrown single crystal leaves the main hand empty");
        Snowball flying = null;
        for (Entity en : r.e.world.getEntities()) {
            if (en instanceof Snowball sb) {
                flying = sb;
            }
        }
        expect(flying != null && flying.getPersistentDataContainer().has(r.e.keys.crystalMarker, HatchlingKeys.STRING)
                && p.getUniqueId().toString().equals(flying.getPersistentDataContainer().get(r.e.keys.thrownBy, HatchlingKeys.STRING)),
                "and sends a tagged snowball");
        expect(p.heard("Crystal thrown"), "and says so");

        // thrown again straight away: ignored
        ItemStack again = crystalIn(r, 2);
        p.getInventory().setItemInMainHand(again);
        l.onInteract(new PlayerInteractEvent(p, Action.RIGHT_CLICK_AIR, again, null));
        expect(again.getAmount() == 2, "a second click inside the cooldown uses nothing");

        // a stack of crystals loses one
        Kit.TestPlayer q = r.e.player("Stack", 0, 64, 0);
        ItemStack stack = crystalIn(r, 3);
        q.getInventory().setItemInMainHand(stack);
        l.onInteract(new PlayerInteractEvent(q, Action.RIGHT_CLICK_BLOCK, stack, r.e.world.getBlockAt(0, 63, 0)));
        expect(stack.getAmount() == 2 && q.getInventory().getItemInMainHand() == stack, "a stack of three becomes two");

        // the off hand: that crystal is the one used up, and the main hand is left alone
        Kit.TestPlayer o = r.e.player("Offhand", 0, 64, 0);
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        o.getInventory().setItemInMainHand(sword);
        ItemStack off = crystalIn(r, 1);
        o.getInventory().setItemInOffHand(off);
        PlayerInteractEvent click = new PlayerInteractEvent(o, Action.RIGHT_CLICK_AIR, off, null);
        click.stubHand = EquipmentSlot.OFF_HAND;
        l.onInteract(click);
        expect(o.getInventory().getItemInOffHand() == null, "a crystal thrown from the off hand is used up from the off hand");
        expect(o.getInventory().getItemInMainHand() == sword, "and the main hand keeps what it held");

        // not a crystal / not a right click
        Kit.TestPlayer n = r.e.player("None", 0, 64, 0);
        ItemStack stone = new ItemStack(Material.STONE);
        n.getInventory().setItemInMainHand(stone);
        l.onInteract(new PlayerInteractEvent(n, Action.RIGHT_CLICK_AIR, stone, null));
        l.onInteract(new PlayerInteractEvent(n, Action.LEFT_CLICK_AIR, crystalIn(r, 1), null));
        expect(n.messages.isEmpty() && n.getInventory().getItemInMainHand() == stone, "other items and left clicks do nothing");
    }

    /** A mob whose custom name cannot be set, so the trait roll fails partway through taming. */
    static final class Stubborn extends Zombie {
        @Override
        public void setCustomName(String name) {
            throw new IllegalStateException("no names today");
        }
    }

    static void tameSurvivesTraitTrouble() {
        // a server with a trait list, so that taming really does roll traits and name the mob
        Kit.TestPlugin plugin = new Kit.TestPlugin();
        plugin.getConfig().set("traits.definitions", List.of(ConfigTest.trait("c", "COSMETIC", "prefix", "Glittering")));
        HatchlingConfig hatch = new HatchlingConfig(plugin);
        hatch.load();
        HatchlingKeys keys = new HatchlingKeys(plugin);
        TraitRegistry registry = new TraitRegistry(plugin);
        registry.load();
        HatchlingManager manager = new HatchlingManager(plugin, hatch, keys, new TraitManager(registry, keys, hatch));
        CrystalTaming taming = new CrystalTaming(hatch, manager, new CrystalItem(keys));
        Env e = new Env();
        Kit.TestPlayer p = e.player("Tamer", 0, 64, 0);

        Zombie fine = e.world.spawn(e.at(2, 64, 2), Zombie.class);
        List<TraitDefinition> got = manager.tame(fine, p.getUniqueId());
        expect(got.size() == 1 && fine.getCustomName() != null && fine.getCustomName().contains("Glittering"),
                "with a trait list, taming rolls a trait and names the mob: " + fine.getCustomName());

        Stubborn z = new Stubborn();
        z.stubSetLocation(e.at(3, 64, 3));
        Bukkit.ENTITIES.put(z.getUniqueId(), z);
        List<TraitDefinition> rolled = manager.tame(z, p.getUniqueId());
        expect(rolled != null && rolled.isEmpty(), "taming still finishes when the trait roll fails");
        expect(manager.isTame(z) && p.getUniqueId().equals(manager.ownerOf(z)), "and the mob is the player's");
        expect(z.getPersistentDataContainer().has(keys.tamedAt, HatchlingKeys.LONG), "with its growth clock started");

        // and through the crystal path: the owner is still told
        Stubborn y = new Stubborn();
        y.stubSetLocation(e.at(4, 64, 4));
        Bukkit.ENTITIES.put(y.getUniqueId(), y);
        p.messages.clear();
        taming.tame(y, p.getUniqueId(), e.at(4, 64, 4));
        expect(manager.isTame(y) && p.heard("Tamed a zombie"), "a crystal tames it and tells the owner anyway: " + p.messages);
    }

    static void endermanRules() {
        Rig r = new Rig();
        HatchlingListener l = new HatchlingListener(r.e.hatch, r.e.manager, r.e.keys);
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Enderman pet = r.spawn(Enderman.class, 5, 64, 5);
        r.e.manager.tame(pet, p.getUniqueId());
        Enderman wild = r.spawn(Enderman.class, 9, 64, 9);
        Zombie zombiePet = r.spawn(Zombie.class, 7, 64, 7);
        r.e.manager.tame(zombiePet, p.getUniqueId());

        EntityTeleportEvent hop = new EntityTeleportEvent(pet, r.e.at(5, 64, 5), r.e.at(30, 64, 30));
        l.onEndermanTeleport(hop);
        expect(hop.isCancelled(), "a tamed Enderman does not teleport away on its own");
        EntityTeleportEvent wildHop = new EntityTeleportEvent(wild, r.e.at(9, 64, 9), r.e.at(30, 64, 30));
        l.onEndermanTeleport(wildHop);
        expect(!wildHop.isCancelled(), "a wild one still does");
        EntityTeleportEvent other = new EntityTeleportEvent(zombiePet, r.e.at(7, 64, 7), r.e.at(30, 64, 30));
        l.onEndermanTeleport(other);
        expect(!other.isCancelled(), "and other tamed mobs are not stopped from teleporting");

        EntityChangeBlockEvent pick = new EntityChangeBlockEvent(pet, r.e.world.getBlockAt(5, 63, 5), Material.AIR);
        l.onEndermanBlocks(pick);
        expect(pick.isCancelled(), "a tamed Enderman does not pick up blocks");
        EntityChangeBlockEvent wildPick = new EntityChangeBlockEvent(wild, r.e.world.getBlockAt(9, 63, 9), Material.AIR);
        l.onEndermanBlocks(wildPick);
        expect(!wildPick.isCancelled(), "a wild one does");
        EntityChangeBlockEvent zombieBreak = new EntityChangeBlockEvent(zombiePet, r.e.world.getBlockAt(7, 63, 7), Material.AIR);
        l.onEndermanBlocks(zombieBreak);
        expect(!zombieBreak.isCancelled(), "and nothing else is affected");

        r.e.plugin.getConfig().set("endermen.stay-put", false);
        r.e.hatch.load();
        EntityTeleportEvent free = new EntityTeleportEvent(pet, r.e.at(5, 64, 5), r.e.at(30, 64, 30));
        l.onEndermanTeleport(free);
        EntityChangeBlockEvent freePick = new EntityChangeBlockEvent(pet, r.e.world.getBlockAt(5, 63, 5), Material.AIR);
        l.onEndermanBlocks(freePick);
        expect(!free.isCancelled() && !freePick.isCancelled(), "with endermen.stay-put off both are allowed");
    }

    /** An Enderman that fires the teleport event when it is moved, the way a real server does. */
    static final class Hopper extends Enderman {
        HatchlingListener listener;
        boolean blocked;
        int hops;

        @Override
        public boolean teleport(Location location) {
            EntityTeleportEvent ev = new EntityTeleportEvent(this, getLocation(), location);
            listener.onEndermanTeleport(ev);
            hops++;
            blocked = ev.isCancelled();
            if (!ev.isCancelled()) {
                stubSetLocation(location);
            }
            return !ev.isCancelled();
        }
    }

    static void selfTeleport() {
        Rig r = new Rig();
        HatchlingListener l = new HatchlingListener(r.e.hatch, r.e.manager, r.e.keys);
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Hopper h = new Hopper();
        h.listener = l;
        h.stubSetLocation(r.e.at(500, 64, 500));
        Bukkit.ENTITIES.put(h.getUniqueId(), h);
        r.e.manager.tame(h, p.getUniqueId());
        expect(!r.e.manager.isSelfTeleport(), "no teleport is under way");
        r.e.manager.reapplyOne(h);
        expect(h.hops == 1 && !h.blocked, "the plugin's own 'come back to your owner' teleport is let through");
        expect(h.getLocation().distance(p.getLocation()) < 1.0, "and the Enderman arrives");
        expect(!r.e.manager.isSelfTeleport(), "after which the plugin stops letting teleports through");
        EntityTeleportEvent wander = new EntityTeleportEvent(h, h.getLocation(), r.e.at(40, 64, 40));
        l.onEndermanTeleport(wander);
        expect(wander.isCancelled(), "its own wandering is blocked again");
    }

    /** A pet NexusBestiary told to stay is not called back to its owner. */
    static void heldPets() {
        Rig r = new Rig();
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Zombie z = r.spawn(Zombie.class, 5, 64, 0);
        r.e.manager.tame(z, p.getUniqueId());
        z.stubSetLocation(r.e.at(500, 64, 500));
        z.getPersistentDataContainer().set(r.e.keys.bestiaryHeld, HatchlingKeys.STRING, "world|500.0|64.0|500.0");
        r.e.manager.reapplyOne(z);
        expect(z.getLocation().getX() == 500.0, "a pet that was told to stay is not teleported to its owner");
        expect(z.getPathfinder().lastEntity == null && z.getPathfinder().lastLocation == null, "and is not sent walking either");
        z.stubSetLocation(r.e.at(20, 64, 0));
        r.e.manager.reapplyOne(z);
        expect(z.getPathfinder().lastEntity == null, "close by, a held pet is still not sent after its owner");
        z.getPersistentDataContainer().remove(r.e.keys.bestiaryHeld);
        r.e.manager.reapplyOne(z);
        expect(z.getPathfinder().lastEntity == p, "once it is released it follows again");
        z.stubSetLocation(r.e.at(500, 64, 500));
        r.e.manager.reapplyOne(z);
        expect(z.getLocation().getX() < 5.0, "and is brought back from far away as before");
    }

    static void ownerFarming() {
        Rig r = new Rig();
        OwnerInteractListener l = new OwnerInteractListener(r.e.hatch, r.e.manager, new ArrayList<String>()::add);
        List<String> log = new ArrayList<>();
        OwnerInteractListener logged = new OwnerInteractListener(r.e.hatch, r.e.manager, log::add);
        Kit.TestPlayer owner = r.e.player("Owner", 0, 64, 0);
        Kit.TestPlayer other = r.e.player("Other", 1, 64, 0);
        Cow cow = r.spawn(Cow.class, 2, 64, 0);
        r.e.manager.tame(cow, owner.getUniqueId());
        Cow wildCow = r.spawn(Cow.class, 3, 64, 0);

        // another plugin refused the click; the owner's bucket click is let through
        owner.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        PlayerInteractEntityEvent milk = refused(owner, cow);
        logged.onInteractEntity(milk);
        expect(!milk.isCancelled(), "the owner can milk their own tamed cow even when another plugin said no");
        expect(log.size() == 1 && log.get(0).contains("not NexusHatchlings"), "and the log says it happened: " + log);
        logged.onInteractEntity(refused(owner, cow));
        expect(log.size() == 1, "once per player and animal type, not once per click");
        Sheep sheep = r.spawn(Sheep.class, 4, 64, 0);
        r.e.manager.tame(sheep, owner.getUniqueId());
        owner.getInventory().setItemInMainHand(new ItemStack(Material.SHEARS));
        PlayerInteractEntityEvent shear = refused(owner, sheep);
        logged.onInteractEntity(shear);
        expect(!shear.isCancelled() && log.size() == 2, "shears on a tamed sheep too, logged separately");
        owner.getInventory().setItemInMainHand(new ItemStack(Material.BOWL));
        PlayerInteractEntityEvent bowl = refused(owner, cow);
        l.onInteractEntity(bowl);
        expect(!bowl.isCancelled(), "a bowl as well");

        // the off hand
        owner.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        owner.getInventory().setItemInOffHand(new ItemStack(Material.BUCKET));
        PlayerInteractEntityEvent off = refused(owner, cow);
        off.setHand(EquipmentSlot.OFF_HAND);
        l.onInteractEntity(off);
        expect(!off.isCancelled(), "a bucket in the off hand counts");
        PlayerInteractEntityEvent mainEmpty = refused(owner, cow);
        l.onInteractEntity(mainEmpty);
        expect(mainEmpty.isCancelled(), "but a click with a stone in the main hand is still refused");

        // things it must not do
        owner.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        PlayerInteractEntityEvent stranger = refused(other, cow);
        other.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        l.onInteractEntity(stranger);
        expect(stranger.isCancelled(), "nobody but the owner gets through");
        PlayerInteractEntityEvent wild = refused(owner, wildCow);
        l.onInteractEntity(wild);
        expect(wild.isCancelled(), "an animal nobody tamed is not touched");
        owner.getInventory().setItemInMainHand(new ItemStack(Material.WHEAT));
        PlayerInteractEntityEvent wheat = refused(owner, cow);
        l.onInteractEntity(wheat);
        expect(wheat.isCancelled(), "other items are not touched");
        owner.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        EnderDragon dragon = r.e.adult(owner, 10, 64, 10);
        PlayerInteractEntityEvent ride = refused(owner, dragon);
        l.onInteractEntity(ride);
        expect(ride.isCancelled(), "a dragon is never un-cancelled");
        PlayerInteractEntityEvent fine = new PlayerInteractEntityEvent(owner, cow);
        l.onInteractEntity(fine);
        expect(!fine.isCancelled(), "a click nobody refused stays as it was");
        l.onInteractEntity(new PlayerInteractEntityEvent(owner, null));
        PlayerInteractEntityEvent nobody = new PlayerInteractEntityEvent(null, cow);
        nobody.setCancelled(true);
        l.onInteractEntity(nobody);
        expect(nobody.isCancelled(), "a click with no player is left alone");
        PlayerInteractEntityEvent empty = refused(owner, cow);
        owner.getInventory().setItemInMainHand(null);
        l.onInteractEntity(empty);
        expect(empty.isCancelled(), "an empty hand is not a farm tool");

        // switched off
        owner.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        r.e.plugin.getConfig().set("owner-interactions.milk-and-shear", false);
        r.e.hatch.load();
        PlayerInteractEntityEvent off2 = refused(owner, cow);
        l.onInteractEntity(off2);
        expect(off2.isCancelled(), "with owner-interactions.milk-and-shear off, nothing is un-cancelled");

        expect(OwnerInteractListener.isFarmTool(Material.BUCKET) && OwnerInteractListener.isFarmTool(Material.BOWL)
                && OwnerInteractListener.isFarmTool(Material.SHEARS) && !OwnerInteractListener.isFarmTool(Material.STONE),
                "the farm tools are exactly bucket, bowl and shears");
    }

    static PlayerInteractEntityEvent refused(Kit.TestPlayer by, Entity target) {
        PlayerInteractEntityEvent ev = new PlayerInteractEntityEvent(by, target);
        ev.setCancelled(true);
        return ev;
    }

    static void protection() {
        Rig r = new Rig();
        HatchlingListener l = new HatchlingListener(r.e.hatch, r.e.manager, r.e.keys);
        Kit.TestPlayer p = r.e.player("Tamer", 0, 64, 0);
        Kit.TestPlayer other = r.e.player("Other", 5, 64, 5);
        Zombie pet = r.spawn(Zombie.class, 2, 64, 2);
        r.e.manager.tame(pet, p.getUniqueId());
        Zombie wild = r.spawn(Zombie.class, 8, 64, 8);

        EntityTargetEvent atOwner = new EntityTargetEvent(pet, p);
        l.onTarget(atOwner);
        expect(atOwner.isCancelled(), "a hatchling never targets its owner");
        EntityTargetEvent atOther = new EntityTargetEvent(pet, other);
        l.onTarget(atOther);
        expect(!atOther.isCancelled(), "but may target someone else");
        EntityTargetEvent atNobody = new EntityTargetEvent(pet, null);
        l.onTarget(atNobody);
        expect(!atNobody.isCancelled(), "or no one");
        EntityTargetEvent wildAtP = new EntityTargetEvent(wild, p);
        l.onTarget(wildAtP);
        expect(!wildAtP.isCancelled(), "a wild mob still targets that player");

        EntityDamageByEntityEvent bite = new EntityDamageByEntityEvent(pet, p, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        l.onDamageByEntity(bite);
        expect(bite.isCancelled(), "and never hurts its owner");
        EntityDamageByEntityEvent biteOther = new EntityDamageByEntityEvent(pet, other, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        l.onDamageByEntity(biteOther);
        expect(!biteOther.isCancelled(), "but can hurt someone else");
        EntityDamageByEntityEvent wildBite = new EntityDamageByEntityEvent(wild, p, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        l.onDamageByEntity(wildBite);
        expect(!wildBite.isCancelled(), "and a wild mob can hurt that player");
        EntityDamageByEntityEvent nobodyBitten = new EntityDamageByEntityEvent(pet, null, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        l.onDamageByEntity(nobodyBitten);
        expect(!nobodyBitten.isCancelled(), "a bite with no victim is left alone");

        // growing hatchlings are protected, except from the void
        EntityDamageEvent fall = new EntityDamageEvent(pet, EntityDamageEvent.DamageCause.FALL);
        l.onDamage(fall);
        expect(fall.isCancelled(), "a growing hatchling takes no damage");
        EntityDamageEvent hole = new EntityDamageEvent(pet, EntityDamageEvent.DamageCause.VOID);
        l.onDamage(hole);
        expect(!hole.isCancelled(), "except from the void");
        EntityDamageEvent wildFall = new EntityDamageEvent(wild, EntityDamageEvent.DamageCause.FALL);
        l.onDamage(wildFall);
        expect(!wildFall.isCancelled(), "a wild mob is not protected");
        r.e.grow(pet, 1.0);
        EntityDamageEvent grown = new EntityDamageEvent(pet, EntityDamageEvent.DamageCause.FALL);
        l.onDamage(grown);
        expect(!grown.isCancelled(), "a fully grown hatchling can be hurt again");
        r.e.grow(pet, 0.5);
        r.e.plugin.getConfig().set("settings.invulnerable-while-growing", false);
        r.e.hatch.load();
        EntityDamageEvent open = new EntityDamageEvent(pet, EntityDamageEvent.DamageCause.FALL);
        l.onDamage(open);
        expect(!open.isCancelled(), "with the protection switched off a growing one can be hurt");

        // waiting crystals can't be picked up by anything
        Item waiting = r.e.world.dropItem(r.e.at(3, 64, 3), r.crystal.create());
        waiting.getPersistentDataContainer().set(r.e.keys.crystalMarker, HatchlingKeys.STRING, "true");
        EntityPickupItemEvent grab = new EntityPickupItemEvent(p, waiting, 0);
        l.onPickup(grab);
        expect(grab.isCancelled(), "nothing can pick up a waiting crystal");
        Item stone = r.e.world.dropItem(r.e.at(3, 64, 3), new ItemStack(Material.STONE));
        EntityPickupItemEvent grabStone = new EntityPickupItemEvent(p, stone, 0);
        l.onPickup(grabStone);
        expect(!grabStone.isCancelled(), "ordinary items are picked up as usual");
        l.onPickup(new EntityPickupItemEvent(p, null, 0));
    }

    /** Small things a crystal does that a player would notice. */
    static void details() {
        Kit.TestPlugin plugin = new Kit.TestPlugin();
        plugin.getConfig().set("traits.definitions", List.of(ConfigTest.trait("c", "COSMETIC", "prefix", "Glittering", "display-name", "Glittering"),
                ConfigTest.trait("d", "COSMETIC", "prefix", "Gloomy", "display-name", "Gloomy")));
        plugin.getConfig().set("traits.count-min", 2);
        plugin.getConfig().set("traits.count-max", 2);
        HatchlingConfig hatch = new HatchlingConfig(plugin);
        hatch.load();
        HatchlingKeys keys = new HatchlingKeys(plugin);
        TraitRegistry registry = new TraitRegistry(plugin);
        registry.load();
        HatchlingManager manager = new HatchlingManager(plugin, hatch, keys, new TraitManager(registry, keys, hatch));
        CrystalTaming taming = new CrystalTaming(hatch, manager, new CrystalItem(keys));
        Env e = new Env();
        Kit.TestPlayer p = e.player("Tamer", 0, 64, 0);
        Zombie z = e.world.spawn(e.at(2, 64, 2), Zombie.class);
        taming.tame(z, p.getUniqueId(), e.at(2, 64, 2));
        expect(p.heard("It rolled") && p.heard("Glittering") && p.heard("Gloomy") && p.messages.toString().contains("\u00a77, \u00a7d"),
                "the owner is told which traits it rolled, comma separated: " + p.messages);

        // a landed crystal is labelled, and dated so it can expire
        Rig r = new Rig();
        Kit.TestPlayer q = r.e.player("Q", 0, 64, 0);
        r.hit(r.flying(q, r.e.at(10, 64, 10)), null);
        Item it = r.items().get(0);
        expect(it.getCustomName() != null && it.getCustomName().contains("Hatchling Crystal") && it.isCustomNameVisible(), "a landed crystal shows its name");
        Long dated = it.getPersistentDataContainer().get(r.e.keys.droppedAt, HatchlingKeys.LONG);
        expect(dated != null && Math.abs(System.currentTimeMillis() - dated) < 60_000L, "and records when it landed");

        // an expired crystal is removed and does not tame the mob next to it on its way out
        Zombie next = r.spawn(Zombie.class, 10, 64, 11);
        it.getPersistentDataContainer().set(r.e.keys.droppedAt, HatchlingKeys.LONG, System.currentTimeMillis() - (r.e.hatch.crystalExpirySeconds() + 60) * 1000L);
        r.scan.scanOnce();
        expect(!it.isValid() && !r.e.manager.isTame(next), "an expired crystal vanishes without taming anything");

        // the throw cancels the vanilla use of the item
        CrystalThrowListener tl = new CrystalThrowListener(r.e.keys, r.crystal);
        Kit.TestPlayer t = r.e.player("T", 0, 64, 0);
        ItemStack st = r.crystal.create();
        t.getInventory().setItemInMainHand(st);
        PlayerInteractEvent click = new PlayerInteractEvent(t, Action.RIGHT_CLICK_AIR, st, null);
        tl.onInteract(click);
        expect(click.useItemInHand() == org.bukkit.event.Event.Result.DENY, "throwing stops the normal use of the held item");
    }

    static void config() {
        Kit.TestPlugin plain = new Kit.TestPlugin();
        HatchlingConfig c = new HatchlingConfig(plain);
        c.load();
        expect(c.endermenStayPut() && c.ownerMilkAndShear(), "both new switches default to on");
        expect(c.isEnabled() && c.invulnerableWhileGrowing() && c.milestonesEnabled() && c.recipeEnabled(), "and so do the older switches");
        Kit.TestPlugin off = new Kit.TestPlugin();
        off.getConfig().set("endermen.stay-put", false);
        off.getConfig().set("owner-interactions.milk-and-shear", false);
        HatchlingConfig d = new HatchlingConfig(off);
        d.load();
        expect(!d.endermenStayPut() && !d.ownerMilkAndShear(), "and read what the file says");
    }
}
