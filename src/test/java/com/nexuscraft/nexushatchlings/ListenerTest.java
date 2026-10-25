package com.nexuscraft.nexushatchlings;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

import static com.nexuscraft.nexushatchlings.Kit.expect;

/** What the server's events do to eggs, dyes, saddles and the dragons' own destructiveness. */
public class ListenerTest {

    public static void main(String[] args) {
        placing();
        egg();
        riding();
        dyes();
        mounting();
        destruction();
        Kit.finish();
    }

    static DragonListener listener(Env e) {
        return new DragonListener(e.cfg, e.svc, e.eggs);
    }

    static Block eggOn(Env e, Material under, int x, int z) {
        e.world.getBlockAt(x, 63, z).setType(under);
        Block egg = e.world.getBlockAt(x, 64, z);
        egg.setType(Material.DRAGON_EGG);
        return egg;
    }

    static void placing() {
        Env e = new Env();
        DragonListener l = listener(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        Block warm = eggOn(e, Material.CAMPFIRE, 0, 0);
        l.onPlace(new BlockPlaceEvent(warm, p));
        expect(e.eggs.has(DragonEggs.posOf(warm)), "an egg put down is tracked");
        expect(p.heard("The egg warms on the fire") && p.heard("15 minute(s)"), "and a warm one says so: " + p.messages);
        Block cold = eggOn(e, Material.STONE, 5, 5);
        Kit.TestPlayer q = e.player("Q", 5, 64, 5);
        l.onPlace(new BlockPlaceEvent(cold, q));
        expect(e.eggs.has(DragonEggs.posOf(cold)), "a cold one is tracked too");
        expect(q.messages.isEmpty(), "but says nothing");
        Block stone = e.world.getBlockAt(9, 64, 9);
        stone.setType(Material.STONE);
        l.onPlace(new BlockPlaceEvent(stone, p));
        expect(!e.eggs.has(DragonEggs.posOf(stone)) && e.eggs.size() == 2, "other blocks are ignored");
        l.onPlace(new BlockPlaceEvent(null, p));
        Block dispensed = eggOn(e, Material.CAMPFIRE, 12, 12);
        l.onPlace(new BlockPlaceEvent(dispensed, null));
        expect(e.eggs.has(DragonEggs.posOf(dispensed)), "an egg placed with no player (a dispenser) is tracked without a fuss");
        e.cfg.enabled = false;
        Block off = eggOn(e, Material.CAMPFIRE, 15, 15);
        l.onPlace(new BlockPlaceEvent(off, p));
        expect(!e.eggs.has(DragonEggs.posOf(off)), "nothing is tracked when dragons are switched off");
    }

    static PlayerInteractEvent click(Kit.TestPlayer p, Action a, Block b) {
        return new PlayerInteractEvent(p, a, null, b);
    }

    static void egg() {
        Env e = new Env();
        DragonListener l = listener(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        Block warm = eggOn(e, Material.CAMPFIRE, 0, 0);
        PlayerInteractEvent right = click(p, Action.RIGHT_CLICK_BLOCK, warm);
        l.onInteract(right);
        expect(right.isCancelled(), "poking a warm egg does not make it jump away");
        expect(e.eggs.has(DragonEggs.posOf(warm)), "and starts tracking it");
        expect(p.heard("The egg is warm: 0% of the way, about 15 minute(s) to go"), "and shows how far along it is: " + p.messages);
        PlayerInteractEvent left = click(p, Action.LEFT_CLICK_BLOCK, warm);
        l.onInteract(left);
        expect(left.isCancelled(), "so does a left click");
        int n = p.messages.size();
        PlayerInteractEvent off = click(p, Action.RIGHT_CLICK_BLOCK, warm);
        off.stubHand = EquipmentSlot.OFF_HAND;
        l.onInteract(off);
        expect(off.isCancelled() && p.messages.size() == n, "the off hand is cancelled but says nothing twice");
        Block cold = eggOn(e, Material.STONE, 6, 6);
        PlayerInteractEvent coldClick = click(p, Action.RIGHT_CLICK_BLOCK, cold);
        l.onInteract(coldClick);
        expect(!coldClick.isCancelled() && !e.eggs.has(DragonEggs.posOf(cold)), "a cold egg behaves like vanilla");
        PlayerInteractEvent walk = click(p, Action.PHYSICAL, warm);
        l.onInteract(walk);
        expect(!walk.isCancelled(), "stepping on it is nothing");
        e.cfg.enabled = false;
        PlayerInteractEvent disabled = click(p, Action.RIGHT_CLICK_BLOCK, warm);
        l.onInteract(disabled);
        expect(!disabled.isCancelled(), "nothing happens when dragons are switched off");
        e.cfg.enabled = true;
        // progress shows later on
        for (int i = 0; i < 90; i++) {
            e.clock.addAndGet(5000);
            e.eggs.tick();
        }
        p.messages.clear();
        l.onInteract(click(p, Action.RIGHT_CLICK_BLOCK, warm));
        expect(p.heard("50% of the way, about 8 minute(s)"), "later: " + p.messages);
    }

    static void riding() {
        Env e = new Env();
        DragonListener l = listener(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        EnderDragon d = e.fledgling(p, 0, 64, 0);
        e.svc.mount(p, d);
        PlayerInteractEvent air = click(p, Action.RIGHT_CLICK_AIR, null);
        l.onInteract(air);
        expect(air.isCancelled(), "the air-click while riding is taken");
        expect(p.heard("Flying"), "and starts the flight: " + p.messages);
        l.onInteract(click(p, Action.RIGHT_CLICK_AIR, null));
        expect(p.last().contains("Hovering"), "again, and it hovers: " + p.last());
        PlayerInteractEvent block = click(p, Action.RIGHT_CLICK_BLOCK, e.world.getBlockAt(0, 63, 0));
        l.onInteract(block);
        expect(block.isCancelled() && p.last().contains("Flying"), "a click on a block toggles it as well");
        int n = p.messages.size();
        PlayerInteractEvent off = click(p, Action.RIGHT_CLICK_AIR, null);
        off.stubHand = EquipmentSlot.OFF_HAND;
        l.onInteract(off);
        expect(!off.isCancelled() && p.messages.size() == n, "the off hand is ignored");
        PlayerInteractEvent hit = click(p, Action.LEFT_CLICK_AIR, null);
        l.onInteract(hit);
        expect(!hit.isCancelled() && p.messages.size() == n, "a left click is ignored");
        // a click on a cold dragon egg is not a flight toggle, even for a rider
        Block coldEgg = eggOn(e, Material.STONE, 8, 8);
        int before = p.messages.size();
        PlayerInteractEvent coldRide = click(p, Action.RIGHT_CLICK_BLOCK, coldEgg);
        l.onInteract(coldRide);
        expect(!coldRide.isCancelled() && p.messages.size() == before, "a rider's click on a cold egg does nothing: " + p.messages);
        Kit.TestPlayer walker = e.player("W", 5, 64, 5);
        PlayerInteractEvent normal = click(walker, Action.RIGHT_CLICK_AIR, null);
        l.onInteract(normal);
        expect(!normal.isCancelled() && walker.messages.isEmpty(), "a player not riding is left alone");
        e.cfg.enabled = false;
        PlayerInteractEvent disabled = click(p, Action.RIGHT_CLICK_AIR, null);
        l.onInteract(disabled);
        expect(!disabled.isCancelled(), "nothing when switched off");
    }

    static PlayerInteractEntityEvent useOn(Kit.TestPlayer p, org.bukkit.entity.Entity target) {
        return new PlayerInteractEntityEvent(p, target);
    }

    static void dyes() {
        Env e = new Env();
        DragonListener l = listener(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        EnderDragon wild = e.baby(1, 64, 0);
        e.svc.setColor(wild, DragonColor.CRIMSON);
        p.getInventory().setItemInMainHand(new ItemStack(Material.BLUE_DYE, 3));
        PlayerInteractEntityEvent ev = useOn(p, wild);
        l.onInteractEntity(ev);
        expect(ev.isCancelled(), "the click is taken");
        expect(e.svc.colorOf(wild) == DragonColor.SAPPHIRE, "blue dye makes it sapphire");
        expect(p.getInventory().getItemInMainHand().getAmount() == 2, "and uses one dye");
        expect(p.heard("turns sapphire"), "and says so: " + p.messages);
        // the same colour again
        p.messages.clear();
        l.onInteractEntity(useOn(p, wild));
        expect(p.heard("already sapphire") && p.getInventory().getItemInMainHand().getAmount() == 2, "same colour: no dye used");
        // last dye in the stack
        p.getInventory().setItemInMainHand(new ItemStack(Material.RED_DYE, 1));
        l.onInteractEntity(useOn(p, wild));
        expect(e.svc.colorOf(wild) == DragonColor.CRIMSON, "red dye makes it crimson");
        expect(p.getInventory().getItemInMainHand().getType() == Material.AIR, "the last dye leaves an empty hand");
        // creative players keep their dye
        p.setGameMode(GameMode.CREATIVE);
        p.getInventory().setItemInMainHand(new ItemStack(Material.BLACK_DYE, 1));
        l.onInteractEntity(useOn(p, wild));
        expect(e.svc.colorOf(wild) == DragonColor.ONYX && p.getInventory().getItemInMainHand().getAmount() == 1, "creative: colour changes, dye stays");
        p.setGameMode(GameMode.SURVIVAL);
        // all sixteen dyes work
        for (DragonColor c : DragonColor.values()) {
            p.getInventory().setItemInMainHand(new ItemStack(Material.valueOf(c.dye()), 5));
            if (e.svc.colorOf(wild) == c) {
                continue;
            }
            l.onInteractEntity(useOn(p, wild));
            expect(e.svc.colorOf(wild) == c, "dye " + c.dye() + " gives " + c.id());
        }
        // through a hitbox part
        p.getInventory().setItemInMainHand(new ItemStack(Material.PINK_DYE, 2));
        l.onInteractEntity(useOn(p, new EnderDragonPart(wild)));
        expect(e.svc.colorOf(wild) == DragonColor.ROSE, "clicking a hitbox part counts as clicking the dragon");
        // somebody else's dragon
        Kit.TestPlayer owner = e.player("Owner", 4, 64, 0);
        EnderDragon theirs = e.tame(e.baby(3, 64, 0), owner, 0.2);
        DragonColor before = e.svc.colorOf(theirs);
        p.messages.clear();
        p.getInventory().setItemInMainHand(new ItemStack(Material.LIME_DYE, 4));
        PlayerInteractEntityEvent denied = useOn(p, theirs);
        l.onInteractEntity(denied);
        expect(denied.isCancelled() && e.svc.colorOf(theirs) == before, "another player's dragon keeps its colour");
        expect(p.getInventory().getItemInMainHand().getAmount() == 4, "and no dye is used");
        expect(p.heard("belongs to someone else"), "and the player is told: " + p.messages);
        owner.getInventory().setItemInMainHand(new ItemStack(Material.LIME_DYE, 4));
        l.onInteractEntity(useOn(owner, theirs));
        expect(e.svc.colorOf(theirs) == DragonColor.LIME, "its owner can dye it");
        // staff colour command
        // sneaking with nothing shows a description
        Kit.TestPlayer s = e.player("S", 0, 64, 0);
        s.setSneaking(true);
        s.getInventory().setItemInMainHand(new ItemStack());
        l.onInteractEntity(useOn(s, theirs));
        expect(s.heard("hatchling") && s.heard("20% grown"), "sneak-click describes the dragon: " + s.messages);
        // neither a dye nor a sneak-click also puts the owner on the dragon
        Kit.TestPlayer rider = e.player("Rider", 30, 64, 30);
        EnderDragon mine = e.fledgling(rider, 30, 64, 30);
        rider.getInventory().setItemInMainHand(new ItemStack(Material.BLACK_DYE, 2));
        e.svc.setColor(mine, DragonColor.IVORY);
        l.onInteractEntity(useOn(rider, mine));
        expect(e.svc.colorOf(mine) == DragonColor.ONYX && e.svc.riddenBy(rider) == null, "a dye on your own flyer only dyes it");
        rider.getInventory().setItemInMainHand(new ItemStack(Material.BLACK_DYE, 2));
        l.onInteractEntity(useOn(rider, mine));
        expect(e.svc.riddenBy(rider) == null && rider.heard("already"), "an unchanged colour does not mount either: " + rider.messages);
        rider.setSneaking(true);
        rider.getInventory().setItemInMainHand(new ItemStack());
        rider.messages.clear();
        l.onInteractEntity(useOn(rider, mine));
        expect(e.svc.riddenBy(rider) == null && rider.heard("grown"), "a sneak-click only describes it: " + rider.messages);
        rider.setSneaking(false);
        l.onInteractEntity(useOn(rider, mine));
        expect(e.svc.riddenBy(rider) == mine, "and a plain click does climb on");
        // a dye wins over sneaking
        s.getInventory().setItemInMainHand(new ItemStack(Material.WHITE_DYE, 1));
        l.onInteractEntity(useOn(s, wild));
        expect(e.svc.colorOf(wild) == DragonColor.IVORY, "a dye is used even when sneaking");
    }

    static void mounting() {
        Env e = new Env();
        DragonListener l = listener(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        EnderDragon ride = e.fledgling(p, 1, 64, 0);
        p.getInventory().setItemInMainHand(new ItemStack());
        PlayerInteractEntityEvent ev = useOn(p, ride);
        l.onInteractEntity(ev);
        expect(ev.isCancelled(), "taken");
        expect(p.getVehicle() == ride, "right-clicking your fledgling climbs on");
        expect(p.heard("You climb on"), "and says how to fly: " + p.messages);
        // the other failures each have their own words
        Kit.TestPlayer q = e.player("Q", 0, 64, 0);
        q.getInventory().setItemInMainHand(new ItemStack());
        l.onInteractEntity(useOn(q, ride));
        expect(q.heard("not yours to ride"), "someone else's dragon: " + q.messages);
        e.cfg.rideOwnerOnly = false;
        q.messages.clear();
        l.onInteractEntity(useOn(q, ride));
        expect(q.heard("Somebody is already riding it"), "occupied: " + q.messages);
        e.cfg.rideOwnerOnly = true;
        EnderDragon baby = e.tame(e.baby(2, 64, 0), p, 0.0);
        Kit.TestPlayer r = e.player("R", 0, 64, 0);
        r.getInventory().setItemInMainHand(new ItemStack());
        l.onInteractEntity(useOn(r, baby));
        expect(r.heard("not yours"), "someone else's hatchling: " + r.messages);
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        o.getInventory().setItemInMainHand(new ItemStack());
        l.onInteractEntity(useOn(o, e.baby(3, 64, 0)));
        expect(o.heard("wild"), "wild: " + o.messages);
        p.leaveVehicle();
        p.messages.clear();
        l.onInteractEntity(useOn(p, baby));
        expect(p.heard("too young"), "too young: " + p.messages);
        e.cfg.rideEnabled = false;
        p.messages.clear();
        l.onInteractEntity(useOn(p, ride));
        expect(p.heard("switched off"), "riding disabled: " + p.messages);
        e.cfg.rideEnabled = true;
        // ignored cases
        Zombie z = new Zombie();
        PlayerInteractEntityEvent zombie = useOn(p, z);
        l.onInteractEntity(zombie);
        expect(!zombie.isCancelled(), "a zombie is not our business");
        EnderDragon vanilla = e.world.spawn(e.at(9, 64, 9), EnderDragon.class);
        PlayerInteractEntityEvent plain = useOn(p, vanilla);
        l.onInteractEntity(plain);
        expect(!plain.isCancelled(), "a vanilla dragon is not either");
        PlayerInteractEntityEvent offhand = useOn(p, ride);
        offhand.setHand(EquipmentSlot.OFF_HAND);
        int n = p.messages.size();
        l.onInteractEntity(offhand);
        expect(!offhand.isCancelled() && p.messages.size() == n, "the off hand is ignored");
        e.cfg.enabled = false;
        PlayerInteractEntityEvent off = useOn(p, ride);
        l.onInteractEntity(off);
        expect(!off.isCancelled(), "nothing when switched off");
    }

    static void destruction() {
        Env e = new Env();
        DragonListener l = listener(e);
        EnderDragon ours = e.baby(0, 64, 0);
        EnderDragon vanilla = e.world.spawn(e.at(9, 64, 9), EnderDragon.class);
        Zombie z = new Zombie();
        EntityExplodeEvent boom = new EntityExplodeEvent(ours, new ArrayList<>(), 1f);
        l.onExplode(boom);
        expect(boom.isCancelled(), "our dragon's explosions break nothing");
        EntityExplodeEvent vanillaBoom = new EntityExplodeEvent(vanilla, new ArrayList<>(), 1f);
        l.onExplode(vanillaBoom);
        expect(!vanillaBoom.isCancelled(), "a vanilla dragon's are not ours to stop");
        EntityExplodeEvent zombieBoom = new EntityExplodeEvent(z, new ArrayList<>(), 1f);
        l.onExplode(zombieBoom);
        expect(!zombieBoom.isCancelled(), "nor a zombie's");
        Block b = e.world.getBlockAt(0, 63, 0);
        EntityChangeBlockEvent change = new EntityChangeBlockEvent(ours, b, Material.AIR);
        l.onChangeBlock(change);
        expect(change.isCancelled(), "our dragon changes no blocks");
        EntityChangeBlockEvent other = new EntityChangeBlockEvent(vanilla, b, Material.AIR);
        l.onChangeBlock(other);
        expect(!other.isCancelled(), "others are left to the server");
        EntityDeathEvent death = new EntityDeathEvent(ours);
        death.setDroppedExp(12000);
        l.onDeath(death);
        expect(death.getDroppedExp() == 0, "our dragon drops no experience");
        EntityDeathEvent boss = new EntityDeathEvent(vanilla);
        boss.setDroppedExp(12000);
        l.onDeath(boss);
        expect(boss.getDroppedExp() == 12000, "a vanilla dragon still drops its own");
        EntityDeathEvent zombieDeath = new EntityDeathEvent(z);
        zombieDeath.setDroppedExp(5);
        l.onDeath(zombieDeath);
        expect(zombieDeath.getDroppedExp() == 5, "and a zombie its own");
    }
}
