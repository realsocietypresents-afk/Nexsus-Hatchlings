package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.nexuscraft.nexushatchlings.Kit.expect;
import static com.nexuscraft.nexushatchlings.Kit.near;

/** The dragon service: creating, colouring, naming, growing, walking, flying, riding and fighting. */
public class ServiceTest {

    public static void main(String[] args) {
        creation();
        recognising();
        taming();
        colours();
        words();
        stages();
        walking();
        following();
        flying();
        mounting();
        riding();
        dismounting();
        orders();
        striking();
        endingOrders();
        releasing();
        scanning();
        announcements();
        aura();
        refreshing();
        describing();
        robustness();
        edges();
        Kit.finish();
    }

    static Team teamOf(DragonColor c) {
        return Bukkit.getScoreboardManager().getMainScoreboard().getTeam(DragonService.TEAM_PREFIX + c.id());
    }

    static void creation() {
        Env e = new Env();
        EnderDragon d = e.baby(0, 64, 0);
        expect(d.isValid(), "baby exists");
        expect(e.svc.isDragon(d), "baby is one of ours");
        expect(!d.hasAI(), "baby has no AI of its own");
        expect(d.isPersistent(), "baby persists");
        expect(!d.getRemoveWhenFarAway(), "baby does not despawn");
        expect(d.getPhase() == EnderDragon.Phase.HOVER, "baby sits in the hover phase");
        near(d.getAttribute(Attribute.GENERIC_SCALE).getBaseValue(), 0.12, 1e-9, "baby starts at the hatchling scale");
        expect(!e.manager.isTame(d), "baby is wild");
        expect(e.manager.ownerOf(d) == null, "baby has no owner");
        expect(e.svc.wordOf(d) == null, "a wild baby has no word");
        DragonColor c = e.svc.colorOf(d);
        expect(c != null, "baby has a colour");
        expect(d.getCustomName() != null && d.getCustomName().equals(DragonService.nameFor(c)), "baby is named after its colour");
        expect(d.getCustomName().startsWith("§" + c.chatCode()) && d.getCustomName().endsWith(c.display() + " Dragon"), "name format");
        expect(d.isCustomNameVisible(), "name shows");
        expect(d.isGlowing(), "baby glows");
        Team t = teamOf(c);
        expect(t != null && t.hasEntry(d.getUniqueId().toString()), "baby is on its colour's team");
        expect(t.getColor() == ChatColor.getByChar(c.chatCode()), "the team shows the colour");
        expect(e.svc.loaded().contains(d), "baby is registered");
        expect(e.svc.stageOf(d) == DragonStage.HATCHLING, "baby is a hatchling");
        near(e.svc.progressOf(d), 0.0, 1e-9, "no growth yet");
        near(d.getLocation().getY(), 64, 1e-9, "baby is where it was put");
        // a second baby gets its own identity
        EnderDragon second = e.baby(5, 64, 5);
        expect(!second.getUniqueId().equals(d.getUniqueId()), "distinct babies");
        expect(e.svc.loaded().size() == 2, "both registered");
        // adopting twice changes nothing
        DragonColor before = e.svc.colorOf(d);
        e.svc.adopt(d, null);
        expect(e.svc.colorOf(d) == before, "adopting again keeps the colour");
        expect(e.svc.loaded().size() == 2, "adopting again does not duplicate");
        // a stranger (a vanilla dragon that has never been touched) is brought fully under control
        EnderDragon stray = e.world.spawn(e.at(9, 64, 9), EnderDragon.class);
        stray.setAI(true);
        stray.setPersistent(false);
        stray.setRemoveWhenFarAway(true);
        stray.setTarget(e.player("Bait", 9, 64, 9));
        stray.setPhase(EnderDragon.Phase.CIRCLING);
        e.svc.adopt(stray, null);
        expect(!stray.hasAI() && stray.isPersistent() && !stray.getRemoveWhenFarAway(), "adopt switches the AI off and keeps it from despawning");
        expect(stray.getTarget() == null && stray.getPhase() == EnderDragon.Phase.HOVER, "and clears its target and phase");
        expect(DragonColor.byId(stray.getPersistentDataContainer().get(e.keys.dragonColor, HatchlingKeys.STRING)) != null, "and gives it a stored colour");
        // colours vary across babies
        Set<DragonColor> seen = new HashSet<>();
        for (int i = 0; i < 80; i++) {
            seen.add(e.svc.colorOf(e.baby(0, 64, 0)));
        }
        expect(seen.size() >= 10, "babies come in many colours (" + seen.size() + ")");
    }

    static void recognising() {
        Env e = new Env();
        EnderDragon d = e.baby(0, 64, 0);
        EnderDragon plain = e.world.spawn(e.at(10, 64, 10), EnderDragon.class);
        expect(!e.svc.isDragon(plain), "a vanilla dragon is not ours");
        expect(!e.svc.isDragon(new Zombie()), "a zombie is not a dragon");
        expect(!e.svc.isDragon(null), "null is not a dragon");
        expect(e.svc.dragonOf(d) == d, "a dragon resolves to itself");
        expect(e.svc.dragonOf(new EnderDragonPart(d)) == d, "a hitbox part resolves to its dragon");
        expect(e.svc.dragonOf(new EnderDragonPart(plain)) == null, "a part of a vanilla dragon resolves to nothing");
        expect(e.svc.dragonOf(plain) == null, "a vanilla dragon resolves to nothing");
        expect(e.svc.dragonOf(null) == null, "null resolves to nothing");
        expect(e.svc.dragonOf(new Zombie()) == null, "a zombie resolves to nothing");
        expect(e.svc.colorOf(plain) == DragonColor.CRIMSON, "an unmarked dragon reads as crimson");
    }

    static void taming() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        EnderDragon d = e.baby(2, 64, 2);
        e.manager.tame(d, owner.getUniqueId());
        expect(e.manager.isTame(d), "tamed");
        expect(owner.getUniqueId().equals(e.manager.ownerOf(d)), "owner recorded");
        expect(e.svc.wordOf(d) != null && CommandWords.valid(e.svc.wordOf(d)), "tamed dragon is given a valid word");
        expect(CommandWords.DEFAULTS.contains(e.svc.wordOf(d)), "its first word is one of the defaults");
        // a plain (vanilla) dragon tamed by a crystal is taken over too
        EnderDragon plain = e.world.spawn(e.at(10, 64, 10), EnderDragon.class);
        e.manager.tame(plain, owner.getUniqueId());
        expect(e.svc.isDragon(plain), "a crystal-tamed vanilla dragon becomes one of ours");
        expect(!plain.hasAI(), "and its AI is switched off");
        expect(e.svc.wordOf(plain) != null, "and it gets a word");
        expect(!e.svc.wordOf(plain).equals(e.svc.wordOf(d)), "words differ between one owner's dragons");
        expect(e.svc.dragonsOf(owner.getUniqueId()).size() == 2, "the owner has two");
        // many dragons, all different words
        Set<String> words = new HashSet<>();
        words.add(e.svc.wordOf(d));
        words.add(e.svc.wordOf(plain));
        for (int i = 0; i < 30; i++) {
            EnderDragon more = e.baby(0, 64, 0);
            e.manager.tame(more, owner.getUniqueId());
            expect(words.add(e.svc.wordOf(more)), "every dragon of one owner has its own word (" + i + ")");
        }
        // another owner is independent
        Kit.TestPlayer other = e.player("Other", 0, 64, 0);
        expect(e.svc.dragonsOf(other.getUniqueId()).isEmpty(), "nobody else owns them");
        EnderDragon theirs = e.baby(0, 64, 0);
        e.manager.tame(theirs, other.getUniqueId());
        expect(e.svc.dragonsOf(other.getUniqueId()).size() == 1, "their own count");
        expect(e.svc.dragonsOf(owner.getUniqueId()).size() == 32, "the first owner's count did not change");
        // tamed a plain mob: untouched by the dragon system
        Zombie z = new Zombie();
        z.stubSetLocation(e.at(0, 64, 0));
        e.manager.tame(z, owner.getUniqueId());
        expect(!e.svc.isDragon(z) && e.svc.loaded().size() == 33, "a zombie crystal-tamed is not a dragon");
        // the follow driver of the manager leaves dragons alone
        e.manager.reapplyOne(d);
        expect(d.getLocation().getX() == 2.0, "the hatchling manager does not path-drive a dragon");
    }

    static void colours() {
        Env e = new Env();
        EnderDragon d = e.baby(0, 64, 0);
        DragonColor first = e.svc.colorOf(d);
        DragonColor next = DragonColor.values()[(first.ordinal() + 1) % 16];
        e.svc.setColor(d, next);
        expect(e.svc.colorOf(d) == next, "colour changes");
        expect(d.getCustomName().equals(DragonService.nameFor(next)), "the default name follows the colour");
        expect(teamOf(next).hasEntry(d.getUniqueId().toString()), "joins the new team");
        expect(!teamOf(first).hasEntry(d.getUniqueId().toString()), "leaves the old team");
        expect(d.isGlowing(), "still glowing");
        // a custom name is kept
        d.setCustomName("Smaug");
        DragonColor third = DragonColor.values()[(next.ordinal() + 1) % 16];
        e.svc.setColor(d, third);
        expect("Smaug".equals(d.getCustomName()), "a custom name is not overwritten");
        expect(e.svc.colorOf(d) == third, "but the colour still changes");
        // every colour works
        for (DragonColor c : DragonColor.values()) {
            e.svc.setColor(d, c);
            expect(e.svc.colorOf(d) == c, "set " + c.id());
            Team t = teamOf(c);
            expect(t != null && t.hasEntry(d.getUniqueId().toString()), "team for " + c.id());
            int memberships = 0;
            for (DragonColor other : DragonColor.values()) {
                Team ot = teamOf(other);
                if (ot != null && ot.hasEntry(d.getUniqueId().toString())) {
                    memberships++;
                }
            }
            expect(memberships == 1, "on exactly one colour team for " + c.id() + " (" + memberships + ")");
        }
        // glow off
        e.cfg.glow = false;
        e.svc.setColor(d, DragonColor.ONYX);
        expect(!d.isGlowing(), "no glow when switched off");
        expect(!teamOf(DragonColor.ONYX).hasEntry(d.getUniqueId().toString()), "and not on a team");
        e.cfg.glow = true;
        e.svc.setColor(d, DragonColor.SKY);
        expect(d.isGlowing() && teamOf(DragonColor.SKY).hasEntry(d.getUniqueId().toString()), "glow back on");
    }

    static void words() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        Kit.TestPlayer other = e.player("Other", 5, 64, 5);
        EnderDragon a = e.tame(e.baby(0, 64, 0), owner, 0);
        EnderDragon b = e.tame(e.baby(1, 64, 0), owner, 0);
        EnderDragon wild = e.baby(2, 64, 0);
        DragonService s = e.svc;
        expect(s.setWord(a, owner.getUniqueId(), "Zorgath") == DragonService.WordResult.OK, "set a word");
        expect("zorgath".equals(s.wordOf(a)), "stored lower case");
        expect(s.byWord(owner.getUniqueId(), "ZORGATH") == a, "found by word ignoring case");
        expect(s.byWord(owner.getUniqueId(), "  zorgath ") == a, "found by word trimmed");
        expect(s.byWord(owner.getUniqueId(), "nothing") == null, "unknown word");
        expect(s.byWord(owner.getUniqueId(), null) == null, "null word");
        expect(s.byWord(other.getUniqueId(), "zorgath") == null, "somebody else cannot call it");
        expect(s.setWord(b, owner.getUniqueId(), "zorgath") == DragonService.WordResult.TAKEN, "a word cannot repeat among one owner's dragons");
        expect(s.setWord(a, owner.getUniqueId(), "zorgath") == DragonService.WordResult.OK, "a dragon may keep its own word");
        expect(s.setWord(a, owner.getUniqueId(), "ZORGATH") == DragonService.WordResult.OK, "or in capitals");
        expect(s.setWord(b, owner.getUniqueId(), "ab") == DragonService.WordResult.INVALID, "too short");
        expect(s.setWord(b, owner.getUniqueId(), "attack") == DragonService.WordResult.INVALID, "reserved");
        expect(s.setWord(b, owner.getUniqueId(), "all") == DragonService.WordResult.INVALID, "'all' is reserved");
        expect(s.setWord(b, owner.getUniqueId(), "dra gon") == DragonService.WordResult.INVALID, "spaces");
        expect(s.setWord(b, owner.getUniqueId(), null) == DragonService.WordResult.INVALID, "null");
        expect(s.setWord(b, other.getUniqueId(), "fine") == DragonService.WordResult.NOT_OWNER, "only the owner may change it");
        expect(s.setWord(wild, owner.getUniqueId(), "fine") == DragonService.WordResult.NOT_OWNER, "a wild dragon has no owner to set it");
        expect(s.setWord(wild, null, "fine") == DragonService.WordResult.NOT_OWNER, "nobody is not the owner");
        expect(s.wordOf(wild) == null, "wild still has none");
        // another owner may use the same word
        EnderDragon theirs = e.tame(e.baby(0, 64, 0), other, 0);
        expect(s.setWord(theirs, other.getUniqueId(), "zorgath") == DragonService.WordResult.OK, "a different owner may use the same word");
        expect(s.byWord(owner.getUniqueId(), "zorgath") == a && s.byWord(other.getUniqueId(), "zorgath") == theirs, "each owner reaches their own");
        expect(s.mayRecolor(owner, a), "owner may recolour");
        expect(!s.mayRecolor(other, a), "others may not");
        expect(s.mayRecolor(other, wild) && s.mayRecolor(owner, wild), "anyone may recolour a wild one");
    }

    static void stages() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        EnderDragon d = e.tame(e.baby(0, 64, 0), owner, 0.0);
        expect(e.svc.stageOf(d) == DragonStage.HATCHLING, "fresh is a hatchling");
        near(e.svc.progressOf(d), 0.0, 0.001, "progress at the start");
        e.grow(d, 0.49);
        expect(e.svc.stageOf(d) == DragonStage.HATCHLING, "49 percent still walks");
        e.grow(d, 0.51);
        expect(e.svc.stageOf(d) == DragonStage.FLEDGLING, "51 percent flies");
        near(e.svc.progressOf(d), 0.51, 0.001, "progress follows the clock");
        e.grow(d, 1.0);
        expect(e.svc.stageOf(d) == DragonStage.ADULT, "100 percent is an adult");
        e.cfg.flyAt = 0.2;
        e.cfg.adultAt = 0.8;
        e.grow(d, 0.3);
        expect(e.svc.stageOf(d) == DragonStage.FLEDGLING, "configured thresholds apply");
        e.grow(d, 0.85);
        expect(e.svc.stageOf(d) == DragonStage.ADULT, "configured adult threshold applies");
        // a wild dragon is judged by its size
        EnderDragon wild = e.baby(3, 64, 3);
        near(e.svc.progressOf(wild), 0.0, 1e-9, "a wild baby is at zero");
        wild.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(0.56);
        near(e.svc.progressOf(wild), 0.5, 1e-9, "half-size is half grown");
        e.cfg.flyAt = 0.5;
        e.cfg.adultAt = 1.0;
        expect(e.svc.stageOf(wild) == DragonStage.FLEDGLING, "half-size is a fledgling");
        wild.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(1.0);
        expect(e.svc.stageOf(wild) == DragonStage.ADULT, "full-size is an adult");
        wild.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(3.0);
        near(e.svc.progressOf(wild), 1.0, 1e-9, "oversize is capped");
        wild.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(0.05);
        near(e.svc.progressOf(wild), 0.0, 1e-9, "undersize is floored");
    }

    static void walking() {
        // a wild baby wanders near where it hatched and stays on the ground
        Env e = new Env();
        EnderDragon d = e.baby(0, 64, 0);
        double far = 0;
        boolean moved = false;
        for (int i = 0; i < 800; i++) {
            e.svc.tick();
            Location l = d.getLocation();
            far = Math.max(far, Math.hypot(l.getX(), l.getZ()));
            moved |= Math.hypot(l.getX(), l.getZ()) > 0.5;
            expect(Math.abs(l.getY() - 64) < 1e-9, "wanderer stays on the ground at tick " + i);
        }
        expect(moved, "a wild baby does wander");
        expect(far <= e.cfg.wanderRadius + 0.5, "and stays within its wander radius (" + far + ")");
        // wandering off: it stays put, and settles to the ground
        Env still = new Env();
        still.cfg.wander = false;
        EnderDragon s = still.baby(0, 70, 0);
        still.tick(1);
        near(s.getLocation().getY(), 68, 1e-9, "a walker drops two blocks a tick");
        still.tick(5);
        near(s.getLocation().getY(), 64, 1e-9, "and lands");
        double x = s.getLocation().getX();
        still.tick(100);
        near(s.getLocation().getX(), x, 1e-9, "no wandering when switched off");
        near(s.getLocation().getY(), 64, 1e-9, "and it stays down");
        // nowhere to land: floating stays put rather than falling through the world
        Env hole = new Env();
        hole.cfg.wander = false;
        EnderDragon h = hole.baby(500, 70, 500);
        hole.tick(10);
        near(h.getLocation().getY(), 70, 1e-9, "with no ground within reach a walker stays where it is");
        // a dragon with no position is skipped without a fuss
        Env nowhere = new Env();
        EnderDragon ghost = new EnderDragon();
        nowhere.svc.adopt(ghost, null);
        nowhere.tick(3);
        expect(ghost.getLocation() == null, "a dragon with no position is not moved");
    }

    static void following() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 20, 64, 0);
        EnderDragon d = e.tame(e.baby(0, 64, 0), owner, 0.0);
        e.tick(120);
        double dist = e.at(20, 64, 0).distance(d.getLocation());
        expect(dist <= 6.0 && dist > 5.0, "a hatchling walks until it is within the follow radius (" + dist + ")");
        near(d.getLocation().getY(), 64, 1e-9, "on the ground");
        near(d.getLocation().getZ(), 0, 1e-6, "straight along the line");
        double stopped = d.getLocation().getX();
        e.tick(50);
        near(d.getLocation().getX(), stopped, 1e-9, "and then it stops");
        // the owner walks away: it follows again
        owner.place(e.world, 40, 64, 0, 0f, 0f);
        e.tick(30);
        expect(d.getLocation().getX() > stopped + 5, "it follows the owner on");
        // walking speed is the configured speed
        Env speed = new Env();
        Kit.TestPlayer o2 = speed.player("Owner", 30, 64, 0);
        EnderDragon walker = speed.tame(speed.baby(0, 64, 0), o2, 0.0);
        speed.tick(10);
        near(walker.getLocation().getX(), 3.0, 1e-6, "ten ticks at 3 blocks a second is 3 blocks");
        // yaw: it faces the way it goes (+x is yaw -90, plus the model offset)
        near(walker.getLocation().getYaw(), DragonMath.dragonYaw(-90, 180), 1e-4, "it faces where it is going");
        // too far: it is brought to the owner
        Env far = new Env();
        Kit.TestPlayer o3 = far.player("Owner", 50, 64, 0);
        EnderDragon lost = far.tame(far.baby(0, 64, 0), o3, 0.0);
        far.tick(1);
        near(lost.getLocation().getX(), 50, 1e-9, "a lost hatchling is brought to its owner");
        near(lost.getLocation().getY(), 64, 1e-9, "onto the ground");
        // a wall it cannot climb
        Env wall = new Env();
        Kit.TestPlayer o4 = wall.player("Owner", 20, 64, 0);
        wall.wall(10, -3, 3, 64, 66);
        EnderDragon blocked = wall.tame(wall.baby(0, 64, 0), o4, 0.0);
        wall.tick(300);
        expect(blocked.getLocation().getX() < 10 && blocked.getLocation().getX() > 9, "a three-high wall stops a hatchling (" + blocked.getLocation().getX() + ")");
        // a step it can climb
        Env step = new Env();
        Kit.TestPlayer o5 = step.player("Owner", 20, 64, 0);
        step.wall(10, -3, 3, 64, 64);
        EnderDragon climber = step.tame(step.baby(0, 64, 0), o5, 0.0);
        boolean onStep = false;
        for (int i = 0; i < 120; i++) {
            step.svc.tick();
            onStep |= Math.abs(climber.getLocation().getY() - 65) < 1e-9;
        }
        expect(onStep, "a one-block step is climbed");
        expect(step.at(20, 64, 0).distance(climber.getLocation()) <= 6.0, "and it gets to the owner");
        near(climber.getLocation().getY(), 64, 1e-9, "and comes back down");
        // a gap it will not cross
        Env gap = new Env();
        Kit.TestPlayer o6 = gap.player("Owner", 20, 64, 0);
        for (int z = -10; z <= 10; z++) {
            gap.world.stubSetBlock(10, 63, z, Material.AIR);
        }
        EnderDragon edge = gap.tame(gap.baby(0, 64, 0), o6, 0.0);
        gap.tick(300);
        expect(edge.getLocation().getX() < 10, "a hatchling will not walk off into a hole");
        // owner in another world, or gone: it stays
        Env away = new Env();
        Kit.TestPlayer o7 = away.player("Owner", 20, 64, 0);
        EnderDragon idle = away.tame(away.baby(0, 64, 0), o7, 0.0);
        o7.online = false;
        away.tick(30);
        near(idle.getLocation().getX(), 0, 1e-9, "an offline owner is not followed");
        o7.online = true;
        o7.place(new org.bukkit.World("nether"), 20, 64, 0, 0f, 0f);
        away.tick(30);
        near(idle.getLocation().getX(), 0, 1e-9, "an owner in another world is not followed");
        // follow-owner switched off
        Env off = new Env();
        off.plugin.getConfig().set("settings.follow-owner", false);
        off.hatch.load();
        Kit.TestPlayer o8 = off.player("Owner", 20, 64, 0);
        EnderDragon stay = off.tame(off.baby(0, 64, 0), o8, 0.0);
        off.tick(30);
        near(stay.getLocation().getX(), 0, 1e-9, "follow-owner off: it stays");
    }

    static void flying() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 30, 64, 0);
        EnderDragon d = e.fledgling(owner, 0, 64, 0);
        e.tick(1);
        near(d.getLocation().getX(), 1.0, 0.01, "a fledgling flies 10 blocks a second");
        expect(d.getLocation().getY() > 64.0 && d.getLocation().getY() < 64.2, "and climbs towards two blocks above its owner");
        e.tick(60);
        double dist = e.at(30, 64, 0).distance(d.getLocation());
        expect(dist <= 6.5, "it arrives (" + dist + ")");
        near(d.getLocation().getY(), 64, 1e-9, "and lands beside its owner");
        // lands gently, 0.6 a tick
        Env soft = new Env();
        Kit.TestPlayer o2 = soft.player("Owner", 0, 64, 0);
        EnderDragon high = soft.fledgling(o2, 0, 80, 0);
        o2.online = false;
        soft.tick(5);
        near(high.getLocation().getY(), 77.0, 1e-6, "a flyer glides down 0.6 blocks a tick");
        soft.tick(40);
        near(high.getLocation().getY(), 64, 1e-9, "and lands");
        // flying owners are followed in the air
        Env air = new Env();
        Kit.TestPlayer o3 = air.player("Owner", 0, 100, 0);
        EnderDragon chaser = air.fledgling(o3, 0, 64, 0);
        air.tick(60);
        expect(air.at(0, 100, 0).distance(chaser.getLocation()) <= 6.5, "it rises to meet an owner in the air (" + chaser.getLocation().getY() + ")");
        double hover = chaser.getLocation().getY();
        air.tick(60);
        near(chaser.getLocation().getY(), hover, 1e-9, "and hovers there instead of sinking back to the ground");
        o3.place(air.world, 0, 70, 0, 0f, 0f);
        air.tick(60);
        expect(air.at(0, 70, 0).distance(chaser.getLocation()) <= 6.5, "it follows the owner back down");
        // too far: brought up to the owner, two blocks above
        Env far = new Env();
        Kit.TestPlayer o4 = far.player("Owner", 50, 70, 0);
        EnderDragon lost = far.fledgling(o4, 0, 64, 0);
        far.tick(1);
        near(lost.getLocation().getX(), 50, 1e-9, "a lost flyer is brought to its owner");
        near(lost.getLocation().getY(), 72, 1e-9, "two blocks above");
        // world height limits hold
        Env sky = new Env();
        Kit.TestPlayer o5 = sky.player("Owner", 0, 318, 0);
        EnderDragon topper = sky.fledgling(o5, 0, 316, 0);
        sky.tick(40);
        expect(topper.getLocation().getY() <= 316.0, "a flyer stays under the build limit (" + topper.getLocation().getY() + ")");
        // flyers fly over walls that stop walkers
        Env over = new Env();
        Kit.TestPlayer o6 = over.player("Owner", 20, 64, 0);
        over.wall(10, -5, 5, 64, 80);
        EnderDragon crosser = over.fledgling(o6, 0, 64, 0);
        over.tick(60);
        expect(over.at(20, 64, 0).distance(crosser.getLocation()) <= 6.5, "a flyer is not stopped by a wall");
    }

    static void mounting() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        Kit.TestPlayer other = e.player("Other", 1, 64, 0);
        EnderDragon baby = e.tame(e.baby(2, 64, 0), owner, 0.1);
        EnderDragon young = e.fledgling(owner, 3, 64, 0);
        EnderDragon wild = e.baby(4, 64, 0);
        DragonService s = e.svc;
        expect(s.mount(owner, wild) == DragonService.MountResult.WILD, "a wild dragon cannot be ridden");
        expect(s.mount(owner, baby) == DragonService.MountResult.TOO_YOUNG, "a hatchling cannot carry anyone");
        expect(s.mount(other, young) == DragonService.MountResult.NOT_OWNER, "only the owner rides");
        e.cfg.rideOwnerOnly = false;
        expect(s.mount(other, young) == DragonService.MountResult.OK, "anyone may ride when owner-only is off");
        s.toggleRide(other);
        expect(s.mount(owner, young) == DragonService.MountResult.OCCUPIED, "one rider at a time");
        e.cfg.rideOwnerOnly = true;
        young.removePassenger(other);
        e.tick(1);
        e.cfg.rideEnabled = false;
        expect(s.mount(owner, young) == DragonService.MountResult.DISABLED, "riding can be switched off");
        e.cfg.rideEnabled = true;
        expect(s.mount(owner, young) == DragonService.MountResult.OK, "the owner climbs on a fledgling");
        expect(young.getPassengers().contains(owner), "the owner is a passenger");
        expect(owner.getVehicle() == young, "and the dragon is the vehicle");
        expect(s.riddenBy(owner) == young, "riddenBy finds it");
        expect(s.isRidden(young), "it is ridden");
        expect(s.riddenBy(other) == null, "other players ride nothing");
        // something else in the saddle counts as occupied
        EnderDragon second = e.fledgling(owner, 6, 64, 0);
        second.addPassenger(new Zombie());
        e.cfg.rideOwnerOnly = false;
        expect(s.mount(e.player("Third", 0, 64, 0), second) == DragonService.MountResult.OCCUPIED, "a passenger of any kind blocks");
        e.cfg.rideOwnerOnly = true;
        // a player on a different kind of vehicle is not riding a dragon
        Kit.TestPlayer cart = e.player("Cart", 0, 64, 0);
        new Zombie().addPassenger(cart);
        expect(s.riddenBy(cart) == null, "a zombie is not a dragon");
        EnderDragon vanilla = e.world.spawn(e.at(0, 64, 0), EnderDragon.class);
        Kit.TestPlayer onVanilla = e.player("Vanilla", 0, 64, 0);
        vanilla.addPassenger(onVanilla);
        expect(s.riddenBy(onVanilla) == null, "a vanilla dragon is not ours");
        expect(s.toggleRide(onVanilla) == null, "toggling off a vanilla dragon does nothing");
        expect(s.toggleRide(cart) == null, "toggling off a zombie does nothing");
        // mounting cancels an attack order
        Env fight = new Env();
        Kit.TestPlayer boss = fight.player("Boss", 0, 64, 0);
        Kit.TestPlayer prey = fight.player("Prey", 20, 64, 0);
        EnderDragon big = fight.adult(boss, 3, 64, 0);
        fight.svc.attack(boss, big, Targeting.Mode.NEAREST, null);
        expect(fight.svc.isAttacking(big), "ordered to attack");
        expect(fight.svc.mount(boss, big) == DragonService.MountResult.OK, "an adult can be ridden");
        expect(!fight.svc.isAttacking(big), "climbing on calls it off");
    }

    static void riding() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        EnderDragon d = e.fledgling(owner, 0, 64, 2);
        e.svc.mount(owner, d);
        // not flying yet: it only turns to face the way the rider looks
        e.tick(2);
        near(d.getLocation().getYaw(), -180, 1e-3, "a hovering dragon faces its rider's way (yaw 0 plus the model offset)");
        near(d.getLocation().getZ(), 2, 1e-9, "and does not move");
        owner.look(90f, 0f);
        e.tick(1);
        near(d.getLocation().getYaw(), DragonMath.dragonYaw(90, 180), 1e-3, "it turns when the rider turns");
        owner.look(0f, 0f);
        e.tick(1);
        expect(Boolean.TRUE.equals(e.svc.toggleRide(owner)), "right-click starts the flight");
        e.tick(10);
        near(d.getLocation().getZ(), 16.0, 1e-6, "flying forward 14 blocks a second for a second");
        near(d.getLocation().getY(), 64.0, 1e-9, "level");
        near(owner.getLocation().getZ(), 16.0, 1e-6, "the rider goes with it");
        near(owner.getLocation().getYaw(), 0, 1e-9, "and keeps looking the same way");
        expect(d.getPassengers().contains(owner), "still aboard");
        // turn and fly the other way
        owner.look(90f, 0f);
        e.tick(5);
        near(d.getLocation().getX(), -7.0, 1e-6, "yaw 90 flies towards -x");
        // up
        owner.look(0f, -30f);
        double y0 = d.getLocation().getY();
        e.tick(10);
        near(d.getLocation().getY() - y0, 14 * Math.sin(Math.toRadians(30)), 0.01, "looking up climbs");
        // stop in the air
        expect(Boolean.FALSE.equals(e.svc.toggleRide(owner)), "right-click again hovers");
        double z = d.getLocation().getZ();
        double y = d.getLocation().getY();
        e.tick(10);
        near(d.getLocation().getZ(), z, 1e-9, "hovering holds position (z)");
        near(d.getLocation().getY(), y, 1e-9, "hovering holds position (y)");
        // a wall stops it
        Env w = new Env();
        Kit.TestPlayer o2 = w.player("Owner", 0, 64, 0);
        w.wall(0, 20, 20, 64, 70);
        w.wall(1, 20, 20, 64, 70);
        w.wall(-1, 20, 20, 64, 70);
        EnderDragon blocked = w.fledgling(o2, 0, 64, 2);
        w.svc.mount(o2, blocked);
        w.svc.toggleRide(o2);
        w.tick(60);
        expect(blocked.getLocation().getZ() < 20 && blocked.getLocation().getZ() > 17, "a wall stops a rider's dragon (" + blocked.getLocation().getZ() + ")");
        near(o2.getLocation().getZ(), blocked.getLocation().getZ(), 1e-9, "with the rider");
        // top and bottom of the world
        Env h = new Env();
        Kit.TestPlayer o3 = h.player("Owner", 0, 300, 0);
        EnderDragon top = h.fledgling(o3, 0, 300, 0);
        h.svc.mount(o3, top);
        h.svc.toggleRide(o3);
        o3.look(0f, -90f);
        h.tick(40);
        near(top.getLocation().getY(), 316, 1e-9, "the ceiling is four below the build limit");
        Env b = new Env();
        Kit.TestPlayer o4 = b.player("Owner", 0, -50, 0);
        EnderDragon bottom = b.fledgling(o4, 0, -50, 0);
        b.svc.mount(o4, bottom);
        b.svc.toggleRide(o4);
        o4.look(0f, 90f);
        b.tick(20);
        near(bottom.getLocation().getY(), -62, 1e-9, "the floor is two above the bottom of the world");
        // speed is configurable
        Env f = new Env();
        f.cfg.rideSpeed = 20;
        Kit.TestPlayer o5 = f.player("Owner", 0, 64, 0);
        EnderDragon fast = f.fledgling(o5, 0, 64, 0);
        f.svc.mount(o5, fast);
        f.svc.toggleRide(o5);
        f.tick(10);
        near(fast.getLocation().getZ(), 20, 1e-6, "ride speed is read from the config");
        // clearance is configurable
        Env c = new Env();
        c.cfg.rideClearance = 1;
        c.wall(0, 12, 12, 66, 70);
        Kit.TestPlayer o6 = c.player("Owner", 0, 64, 0);
        EnderDragon under = c.fledgling(o6, 0, 64, 0);
        c.svc.mount(o6, under);
        c.svc.toggleRide(o6);
        c.tick(30);
        expect(under.getLocation().getZ() > 20, "with a clearance of 1 it slips under a ceiling two blocks up");
        // riding dragons do not follow their owner home
        Env r = new Env();
        Kit.TestPlayer o7 = r.player("Owner", 0, 64, 0);
        EnderDragon staying = r.fledgling(o7, 0, 64, 0);
        r.svc.mount(o7, staying);
        o7.look(0f, 0f);
        r.tick(20);
        near(staying.getLocation().getZ(), 0, 1e-9, "a hovering mount does not wander");
    }

    static void dismounting() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        EnderDragon d = e.fledgling(owner, 0, 64, 0);
        e.svc.mount(owner, d);
        e.svc.toggleRide(owner);
        owner.look(0f, -45f);
        e.tick(30);
        double high = d.getLocation().getY();
        expect(high > 80, "well up in the air");
        d.removePassenger(owner);
        e.tick(1);
        expect(!e.svc.isRidden(d), "the ride ends when the rider leaves");
        expect(owner.effects.size() == 1, "a rider who gets off in mid-air is given slow falling");
        expect(owner.effects.get(0).getType() == PotionEffectType.SLOW_FALLING, "it is slow falling");
        expect(owner.effects.get(0).getDuration() == 120, "for six seconds");
        e.tick(5);
        expect(owner.effects.size() == 1, "and only once");
        // on the ground: nothing
        Env g = new Env();
        Kit.TestPlayer o2 = g.player("Owner", 0, 64, 0);
        EnderDragon low = g.fledgling(o2, 0, 64, 0);
        g.svc.mount(o2, low);
        low.removePassenger(o2);
        g.tick(1);
        expect(o2.effects.isEmpty(), "no slow falling when stepping off on the ground");
        // up to three blocks over the ground still counts as ground
        Env n = new Env();
        Kit.TestPlayer o3 = n.player("Owner", 0, 66, 0);
        EnderDragon near3 = n.fledgling(o3, 0, 66, 0);
        n.svc.mount(o3, near3);
        near3.removePassenger(o3);
        n.tick(1);
        expect(o3.effects.isEmpty(), "two blocks up is a short drop");
        // slow fall off
        Env off = new Env();
        off.cfg.slowFallSeconds = 0;
        Kit.TestPlayer o4 = off.player("Owner", 0, 100, 0);
        EnderDragon d4 = off.fledgling(o4, 0, 100, 0);
        off.svc.mount(o4, d4);
        d4.removePassenger(o4);
        off.tick(1);
        expect(o4.effects.isEmpty(), "slow falling can be switched off");
        // rider logs off
        Env q = new Env();
        Kit.TestPlayer o5 = q.player("Owner", 0, 100, 0);
        EnderDragon d5 = q.fledgling(o5, 0, 100, 0);
        q.svc.mount(o5, d5);
        o5.online = false;
        q.tick(1);
        expect(!q.svc.isRidden(d5), "an offline rider ends the ride");
        expect(d5.getPassengers().isEmpty(), "and is taken off");
        expect(o5.effects.isEmpty(), "no effect for someone who is not there");
        // growth runs backwards (released and re-tamed, or config change): thrown off
        Env s = new Env();
        Kit.TestPlayer o6 = s.player("Owner", 0, 64, 0);
        EnderDragon d6 = s.fledgling(o6, 0, 64, 0);
        s.svc.mount(o6, d6);
        s.grow(d6, 0.1);
        s.tick(1);
        expect(!s.svc.isRidden(d6) && d6.getPassengers().isEmpty(), "a dragon that can no longer fly puts its rider down");
        // riding switched off while aboard
        Env sw = new Env();
        Kit.TestPlayer o7 = sw.player("Owner", 0, 64, 0);
        EnderDragon d7 = sw.fledgling(o7, 0, 64, 0);
        sw.svc.mount(o7, d7);
        sw.cfg.rideEnabled = false;
        sw.tick(1);
        expect(!sw.svc.isRidden(d7) && d7.getPassengers().isEmpty(), "switching riding off puts everyone down");
        // after the ride the dragon follows its owner again
        Env fl = new Env();
        Kit.TestPlayer o8 = fl.player("Owner", 0, 64, 0);
        EnderDragon d8 = fl.fledgling(o8, 0, 64, 0);
        fl.svc.mount(o8, d8);
        fl.svc.toggleRide(o8);
        fl.tick(10);
        d8.removePassenger(o8);
        o8.place(fl.world, 0, 64, 0, 0f, 0f);
        fl.tick(60);
        expect(fl.at(0, 64, 0).distance(d8.getLocation()) <= 6.5, "after the ride it comes back to its owner");
    }

    static Env arena() {
        return new Env();
    }

    static void orders() {
        Env e = arena();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        Kit.TestPlayer prey = e.player("Prey", 30, 64, 0);
        EnderDragon d = e.adult(owner, 3, 64, 0);
        DragonService s = e.svc;
        DragonService.OrderResult r = s.attack(owner, d, Targeting.Mode.NEAREST, null);
        expect(r.status() == DragonService.Status.STARTED, "an adult takes the order");
        expect(r.names().equals(java.util.List.of("Prey")), "and goes after the only player around");
        expect(s.isAttacking(d), "it is attacking");
        expect(e.world.stubSounds.contains(Sound.ENTITY_ENDER_DRAGON_GROWL), "it roars");
        expect(s.stop(d), "stop reports it had an order");
        expect(!s.isAttacking(d), "stopped");
        expect(!s.stop(d), "stop reports nothing to stop the second time");
        // who may give orders
        Kit.TestPlayer stranger = e.player("Stranger", 0, 64, 0);
        expect(s.attack(stranger, d, Targeting.Mode.NEAREST, null).status() == DragonService.Status.NOT_OWNER, "only the owner commands");
        EnderDragon wild = e.baby(0, 64, 0);
        expect(s.attack(owner, wild, Targeting.Mode.NEAREST, null).status() == DragonService.Status.NOT_OWNER, "a wild dragon obeys nobody");
        // not grown
        EnderDragon teen = e.fledgling(owner, 1, 64, 0);
        expect(s.attack(owner, teen, Targeting.Mode.NEAREST, null).status() == DragonService.Status.NOT_ADULT, "a fledgling cannot fight yet");
        EnderDragon baby = e.tame(e.baby(1, 64, 0), owner, 0.0);
        expect(s.attack(owner, baby, Targeting.Mode.NEAREST, null).status() == DragonService.Status.NOT_ADULT, "a hatchling cannot fight");
        // switched off
        e.cfg.attackEnabled = false;
        expect(s.attack(owner, d, Targeting.Mode.NEAREST, null).status() == DragonService.Status.DISABLED, "attacks can be switched off");
        e.cfg.attackEnabled = true;
        // busy
        EnderDragon mount = e.adult(owner, 2, 64, 0);
        s.mount(owner, mount);
        expect(s.attack(owner, mount, Targeting.Mode.NEAREST, null).status() == DragonService.Status.BUSY, "a dragon being ridden cannot be sent off");
        mount.removePassenger(owner);
        e.tick(1);
        // distance and worlds
        owner.place(e.world, 300, 64, 0, 0f, 0f);
        expect(s.attack(owner, d, Targeting.Mode.NEAREST, null).status() == DragonService.Status.FAR_AWAY, "too far to hear");
        owner.place(e.world, 203.5, 64, 0, 0f, 0f);
        expect(s.attack(owner, d, Targeting.Mode.NEAREST, null).status() == DragonService.Status.FAR_AWAY, "just past the command range");
        owner.place(e.world, 203, 64, 0, 0f, 0f);
        expect(s.attack(owner, d, Targeting.Mode.NEAREST, null).status() != DragonService.Status.FAR_AWAY, "exactly at the command range is still in range");
        s.stop(d);
        owner.place(e.world, 202, 64, 0, 0f, 0f);
        expect(s.attack(owner, d, Targeting.Mode.NEAREST, null).status() != DragonService.Status.FAR_AWAY, "just inside the command range");
        s.stop(d);
        owner.place(new org.bukkit.World("nether"), 0, 64, 0, 0f, 0f);
        expect(s.attack(owner, d, Targeting.Mode.NEAREST, null).status() == DragonService.Status.WRONG_WORLD, "another world");
        EnderDragon nowhere = new EnderDragon();
        e.svc.adopt(nowhere, owner.getUniqueId());
        nowhere.getPersistentDataContainer().set(e.keys.owner, HatchlingKeys.STRING, owner.getUniqueId().toString());
        e.grow(nowhere, 1.0);
        owner.place(e.world, 0, 64, 0, 0f, 0f);
        expect(s.attack(owner, nowhere, Targeting.Mode.NEAREST, null).status() == DragonService.Status.WRONG_WORLD, "a dragon with no position cannot be sent");

        // targets
        Env t = arena();
        Kit.TestPlayer o = t.player("Owner", 0, 64, 0);
        EnderDragon big = t.adult(o, 3, 64, 0);
        DragonService.OrderResult none = t.svc.attack(o, big, Targeting.Mode.NEAREST, null);
        expect(none.status() == DragonService.Status.NO_TARGET && none.reason() == Targeting.Reason.NOBODY_IN_RANGE, "nobody about");
        Kit.TestPlayer near = t.player("Near", 10, 64, 0);
        Kit.TestPlayer mid = t.player("Mid", 20, 64, 0);
        Kit.TestPlayer far = t.player("Far", 100, 64, 0);
        Kit.TestPlayer pal = t.player("Pal", 5, 64, 0);
        Kit.TestPlayer creative = t.player("Creative", 6, 64, 0);
        creative.setGameMode(GameMode.CREATIVE);
        Kit.TestPlayer spectator = t.player("Spectator", 7, 64, 0);
        spectator.setGameMode(GameMode.SPECTATOR);
        Kit.TestPlayer dead = t.player("Dead", 8, 64, 0);
        dead.dead = true;
        t.houses.pals.add(o.getUniqueId());
        t.houses.pals.add(pal.getUniqueId());
        DragonService.OrderResult nearest = t.svc.attack(o, big, Targeting.Mode.NEAREST, null);
        expect(nearest.status() == DragonService.Status.STARTED && nearest.names().equals(java.util.List.of("Near")),
                "nearest valid player: " + nearest.names());
        t.svc.stop(big);
        DragonService.OrderResult all = t.svc.attack(o, big, Targeting.Mode.ALL, null);
        expect(all.names().equals(java.util.List.of("Near", "Mid")), "all valid players near it, not allies, owner, creative, spectators, the dead or the far: " + all.names());
        t.svc.stop(big);
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "mid").names().equals(java.util.List.of("Mid")), "a named player");
        t.svc.stop(big);
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "Pal").reason() == Targeting.Reason.ALLY, "an ally by name");
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "Owner").reason() == Targeting.Reason.OWNER, "the owner by name");
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "Creative").reason() == Targeting.Reason.CANNOT_BE_HURT, "a creative player by name");
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "Dead").reason() == Targeting.Reason.CANNOT_BE_HURT, "a dead player by name");
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "Far").reason() == Targeting.Reason.TOO_FAR, "a far player by name");
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "Ghosty").reason() == Targeting.Reason.NOT_FOUND, "a stranger by name");
        expect(!t.svc.isAttacking(big), "none of those started anything");
        // allies from NexusHouses can be switched off
        t.cfg.useHouses = false;
        expect(t.svc.attack(o, big, Targeting.Mode.NAMED, "Pal").status() == DragonService.Status.STARTED, "with houses switched off, an ally is fair game");
        t.svc.stop(big);
        t.cfg.useHouses = true;
        // range comes from the dragon, not the owner
        Env rng = arena();
        Kit.TestPlayer ro = rng.player("Owner", 0, 64, 0);
        EnderDragon scout = rng.adult(ro, 50, 64, 0);
        rng.player("Prey", 100, 64, 0);
        expect(rng.svc.attack(ro, scout, Targeting.Mode.NEAREST, null).status() == DragonService.Status.STARTED, "range is measured from the dragon (prey is 50 from it, 100 from the owner)");
        rng.svc.stop(scout);
        rng.cfg.targetRange = 40;
        expect(rng.svc.attack(ro, scout, Targeting.Mode.NEAREST, null).reason() == Targeting.Reason.NOBODY_IN_RANGE, "target range is configurable");
    }

    static void striking() {
        Env e = arena();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        Kit.TestPlayer prey = e.player("Prey", 30, 64, 0);
        EnderDragon d = e.adult(owner, 3, 64, 0);
        e.svc.attack(owner, d, Targeting.Mode.NEAREST, null);
        e.tick(1);
        near(d.getLocation().getX(), 4.4, 0.05, "it flies at the target at 14 blocks a second");
        expect(prey.damageCalls == 0, "not close enough to breathe yet");
        e.tick(7);
        expect(prey.damageCalls == 1, "it breathes once it is in range (" + prey.damageCalls + ")");
        near(prey.lastDamage, 8.0, 1e-9, "for the configured damage");
        expect(prey.lastDamager == d, "and the dragon is the one credited");
        expect(prey.getFireTicks() == 100, "and sets them alight for five seconds");
        expect(e.world.stubParticles.contains(Particle.DRAGON_BREATH), "with a trail of breath");
        expect(e.world.stubSounds.contains(Sound.ENTITY_ENDER_DRAGON_SHOOT), "and a sound");
        double dist = d.getLocation().distance(prey.getLocation());
        expect(dist <= 22.0, "it was within strike range (" + dist + ")");
        e.tick(92);
        expect(prey.damageCalls == 7, "it breathes every 30 ticks (" + prey.damageCalls + " in 200 ticks)");
        expect(d.getLocation().distance(new Location(e.world, 30, 70, 0)) <= 2.0, "hovering about six blocks above its target (" + d.getLocation() + ")");
        expect(owner.damageCalls == 0, "the owner is never hurt");
        // the interval is configurable
        Env f = arena();
        f.cfg.intervalTicks = 10;
        Kit.TestPlayer o2 = f.player("Owner", 0, 64, 0);
        Kit.TestPlayer p2 = f.player("Prey", 5, 64, 0);
        EnderDragon d2 = f.adult(o2, 3, 64, 0);
        f.svc.attack(o2, d2, Targeting.Mode.NEAREST, null);
        f.tick(50);
        expect(p2.damageCalls == 10, "strikes every 10 ticks when set so (" + p2.damageCalls + ")");
        // a shielded victim (protection plugin cancelled the damage) is not set on fire
        Env g = arena();
        Kit.TestPlayer o3 = g.player("Owner", 0, 64, 0);
        Kit.TestPlayer p3 = g.player("Prey", 5, 64, 0);
        p3.shielded = true;
        EnderDragon d3 = g.adult(o3, 3, 64, 0);
        g.svc.attack(o3, d3, Targeting.Mode.NEAREST, null);
        g.tick(10);
        expect(p3.getFireTicks() == 0, "damage that was cancelled does not burn");
        expect(p3.getHealth() == 20.0, "and does not hurt");
        // fire can be switched off
        Env h = arena();
        h.cfg.fireSeconds = 0;
        Kit.TestPlayer o4 = h.player("Owner", 0, 64, 0);
        Kit.TestPlayer p4 = h.player("Prey", 5, 64, 0);
        p4.setFireTicks(60);
        EnderDragon d4 = h.adult(o4, 3, 64, 0);
        h.svc.attack(o4, d4, Targeting.Mode.NEAREST, null);
        h.tick(10);
        expect(p4.damageCalls > 0 && p4.getFireTicks() == 60, "no fire when fire-seconds is 0, and a fire already burning is left as it was");
        // everybody in range gets breathed on, everybody out of strike range does not
        Env a = arena();
        Kit.TestPlayer o5 = a.player("Owner", 0, 64, 0);
        Kit.TestPlayer x = a.player("X", 10, 64, 0);
        Kit.TestPlayer y = a.player("Y", 12, 64, 6);
        Kit.TestPlayer z = a.player("Z", 60, 64, 0);
        EnderDragon d5 = a.adult(o5, 3, 64, 0);
        a.svc.attack(o5, d5, Targeting.Mode.ALL, null);
        a.tick(8);
        expect(x.damageCalls >= 1 && y.damageCalls >= 1, "all mode burns everyone in reach");
        expect(z.damageCalls == 0, "but not someone beyond strike range (60 blocks off)");
        expect(x.damageCalls == y.damageCalls, "at the same moments");
        // named: only that one
        Env n = arena();
        Kit.TestPlayer o6 = n.player("Owner", 0, 64, 0);
        Kit.TestPlayer p6 = n.player("Pick", 10, 64, 0);
        Kit.TestPlayer q6 = n.player("Bystander", 12, 64, 0);
        EnderDragon d6 = n.adult(o6, 3, 64, 0);
        n.svc.attack(o6, d6, Targeting.Mode.NAMED, "pick");
        n.tick(60);
        expect(p6.damageCalls > 0, "the named player is hit");
        expect(q6.damageCalls == 0, "a bystander is not");
        // a protective house alliance made after the order still protects
        Env w = arena();
        Kit.TestPlayer o7 = w.player("Owner", 0, 64, 0);
        Kit.TestPlayer p7 = w.player("Prey", 20, 64, 0);
        EnderDragon d7 = w.adult(o7, 3, 64, 0);
        w.svc.attack(o7, d7, Targeting.Mode.NEAREST, null);
        w.tick(3);
        w.houses.pals.add(o7.getUniqueId());
        w.houses.pals.add(p7.getUniqueId());
        int before = p7.damageCalls;
        w.tick(40);
        expect(p7.damageCalls == before, "a player who becomes an ally mid-fight is spared");
        expect(!w.svc.isAttacking(d7), "and the dragon gives up");
        expect(owner.heard("nobody left") || o7.heard("nobody left"), "and the owner is told");
    }

    static void endingOrders() {
        // the order runs out
        Env e = arena();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        Kit.TestPlayer prey = e.player("Prey", 20, 64, 0);
        EnderDragon d = e.adult(owner, 3, 64, 0);
        e.svc.attack(owner, d, Targeting.Mode.NEAREST, null);
        e.tick(449);
        expect(e.svc.isAttacking(d), "still fighting just before 45 seconds");
        e.tick(1);
        expect(!e.svc.isAttacking(d), "done at 45 seconds");
        int hits = prey.damageCalls;
        e.tick(60);
        expect(prey.damageCalls == hits, "and it stops hurting");
        // after the fight it comes home
        owner.place(e.world, 0, 64, 0, 0f, 0f);
        e.tick(200);
        expect(e.at(0, 64, 0).distance(d.getLocation()) <= 6.5, "it flies back to its owner");
        near(d.getLocation().getY(), 64, 1e-9, "and lands");
        // owner leaves
        Env o = arena();
        Kit.TestPlayer owner2 = o.player("Owner", 0, 64, 0);
        o.player("Prey", 20, 64, 0);
        EnderDragon d2 = o.adult(owner2, 3, 64, 0);
        o.svc.attack(owner2, d2, Targeting.Mode.NEAREST, null);
        owner2.online = false;
        o.tick(1);
        expect(!o.svc.isAttacking(d2), "an owner who logs off ends the fight");
        // target leaves, dies, changes mode, or changes world
        for (int variant = 0; variant < 4; variant++) {
            Env v = arena();
            Kit.TestPlayer ow = v.player("Owner", 0, 64, 0);
            Kit.TestPlayer pr = v.player("Prey", 20, 64, 0);
            EnderDragon dr = v.adult(ow, 3, 64, 0);
            v.svc.attack(ow, dr, Targeting.Mode.NEAREST, null);
            v.tick(2);
            switch (variant) {
                case 0 -> pr.online = false;
                case 1 -> pr.dead = true;
                case 2 -> pr.setGameMode(GameMode.CREATIVE);
                default -> pr.place(new org.bukkit.World("nether"), 20, 64, 0, 0f, 0f);
            }
            v.tick(1);
            expect(!v.svc.isAttacking(dr), "the fight ends when the target is out of play (variant " + variant + ")");
            expect(ow.heard("nobody left"), "and the owner hears about it (variant " + variant + ")");
        }
        // attack switched off mid-fight
        Env s = arena();
        Kit.TestPlayer ow2 = s.player("Owner", 0, 64, 0);
        s.player("Prey", 20, 64, 0);
        EnderDragon d3 = s.adult(ow2, 3, 64, 0);
        s.svc.attack(ow2, d3, Targeting.Mode.NEAREST, null);
        s.cfg.attackEnabled = false;
        s.tick(1);
        expect(!s.svc.isAttacking(d3), "switching attacks off ends them");
        // growth taken away mid-fight
        Env g = arena();
        Kit.TestPlayer ow3 = g.player("Owner", 0, 64, 0);
        g.player("Prey", 20, 64, 0);
        EnderDragon d4 = g.adult(ow3, 3, 64, 0);
        g.svc.attack(ow3, d4, Targeting.Mode.NEAREST, null);
        g.grow(d4, 0.6);
        g.tick(1);
        expect(!g.svc.isAttacking(d4), "a dragon that is no longer an adult stands down");
        // an ally chosen as the dragon's target is protected only while allied; the dragon moves toward the nearest of several
        Env m = arena();
        Kit.TestPlayer ow4 = m.player("Owner", 0, 64, 0);
        Kit.TestPlayer far = m.player("Far", 40, 64, 0);
        Kit.TestPlayer close = m.player("Close", -30, 64, 0);
        EnderDragon d5 = m.adult(ow4, 0, 64, 0);
        m.svc.attack(ow4, d5, Targeting.Mode.ALL, null);
        m.tick(5);
        expect(d5.getLocation().getX() < 0, "the dragon heads for the nearest of its targets");
        expect(far.damageCalls == 0, "and the far one is out of reach for now");
    }

    static void releasing() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        Kit.TestPlayer prey = e.player("Prey", 20, 64, 0);
        EnderDragon d = e.adult(owner, 3, 64, 0);
        String word = e.svc.wordOf(d);
        expect(word != null, "it has a word");
        e.svc.attack(owner, d, Targeting.Mode.NEAREST, null);
        e.manager.reapplyOne(d);
        e.manager.release(d);
        expect(!e.manager.isTame(d), "released");
        expect(e.svc.wordOf(d) == null, "a released dragon forgets its word");
        expect(!e.svc.isAttacking(d), "and its order");
        expect(e.svc.isDragon(d), "but is still a dragon");
        expect(e.svc.byWord(owner.getUniqueId(), word) == null, "the old owner can no longer call it");
        expect(e.svc.stageOf(d) == DragonStage.ADULT, "it is judged by its size once wild");
        near(prey.lastDamage, 0, 1e-9, "no damage was done");
        // release while ridden
        EnderDragon r = e.fledgling(owner, 1, 64, 0);
        e.svc.mount(owner, r);
        e.manager.release(r);
        expect(r.getPassengers().isEmpty(), "release puts the rider down");
        expect(!e.svc.isRidden(r), "and ends the ride");
        // release of a non-dragon is untouched
        Zombie z = new Zombie();
        z.stubSetLocation(e.at(0, 64, 0));
        e.manager.tame(z, owner.getUniqueId());
        e.manager.release(z);
        expect(!e.manager.isTame(z), "ordinary hatchlings still release");
        // release of a vanilla dragon never adopted: still fine
        EnderDragon plain = e.world.spawn(e.at(5, 64, 5), EnderDragon.class);
        e.manager.release(plain);
        expect(!e.svc.isDragon(plain), "releasing something never ours changes nothing");
        // a released dragon can be tamed again and gets a fresh word
        e.manager.tame(d, owner.getUniqueId());
        expect(e.svc.wordOf(d) != null, "tamed again, it gets a word");
    }

    static void scanning() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        // a marked dragon the service has not met yet
        EnderDragon marked = e.world.spawn(e.at(0, 64, 0), EnderDragon.class);
        marked.getPersistentDataContainer().set(e.keys.dragonMark, HatchlingKeys.INTEGER, 1);
        // a tamed one that was tamed before the dragon system existed
        EnderDragon old = e.world.spawn(e.at(0, 64, 0), EnderDragon.class);
        old.getPersistentDataContainer().set(e.keys.owner, HatchlingKeys.STRING, owner.getUniqueId().toString());
        old.getPersistentDataContainer().set(e.keys.tamedAt, HatchlingKeys.LONG, System.currentTimeMillis());
        // a vanilla boss
        EnderDragon boss = e.world.spawn(e.at(0, 64, 0), EnderDragon.class);
        expect(e.svc.loaded().isEmpty(), "nothing registered before the scan");
        e.svc.scan();
        expect(e.svc.loaded().contains(marked), "the scan finds a marked dragon");
        expect(e.svc.loaded().contains(old), "and a tamed one");
        expect(!e.svc.loaded().contains(boss), "but leaves a vanilla boss alone");
        expect(!marked.hasAI() && !old.hasAI(), "adopted dragons have their AI off");
        expect(boss.hasAI(), "the boss keeps its own AI");
        expect(e.svc.isDragon(old), "the old tame one is marked now");
        expect(e.svc.wordOf(old) != null, "and has a word");
        expect(e.svc.wordOf(marked) == null, "an unowned one has none");
        int count = e.svc.loaded().size();
        e.svc.scan();
        expect(e.svc.loaded().size() == count, "scanning again adds nothing");
        // gone dragons are forgotten
        marked.remove();
        e.svc.scan();
        expect(!e.svc.loaded().contains(marked), "a removed dragon is forgotten");
        // forgetting clears orders and rides
        Kit.TestPlayer prey = e.player("Prey", 20, 64, 0);
        EnderDragon adult = e.adult(owner, 3, 64, 0);
        e.svc.attack(owner, adult, Targeting.Mode.NEAREST, null);
        adult.remove();
        e.svc.scan();
        expect(!e.svc.isAttacking(adult), "its order is forgotten with it");
        EnderDragon rider = e.fledgling(owner, 3, 64, 0);
        e.svc.mount(owner, rider);
        rider.remove();
        e.svc.scan();
        expect(!e.svc.isRidden(rider), "and its ride");
        // ticking over a removed dragon is harmless
        EnderDragon doomed = e.baby(0, 64, 0);
        doomed.remove();
        e.tick(3);
    }

    static void announcements() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        EnderDragon d = e.tame(e.baby(0, 64, 0), owner, 0.0);
        e.svc.scan();
        expect(owner.messages.isEmpty(), "the first sight says nothing");
        e.grow(d, 0.6);
        e.svc.scan();
        expect(owner.heard("grown wings") && owner.heard("right-click it to climb on"), "reaching fledgling is announced");
        int n = owner.messages.size();
        e.svc.scan();
        expect(owner.messages.size() == n, "and only once");
        e.grow(d, 1.0);
        e.svc.scan();
        expect(owner.heard("fully grown") && owner.heard("/dragon " + e.svc.wordOf(d)), "reaching adult is announced with the word");
        n = owner.messages.size();
        e.grow(d, 0.2);
        e.svc.scan();
        expect(owner.messages.size() == n, "going backwards says nothing");
        // jumping straight to adult
        Env j = new Env();
        Kit.TestPlayer o2 = j.player("Owner", 0, 64, 0);
        EnderDragon d2 = j.tame(j.baby(0, 64, 0), o2, 0.0);
        j.svc.scan();
        j.grow(d2, 1.0);
        j.svc.scan();
        expect(o2.heard("fully grown") && !o2.heard("grown wings"), "skipping a stage announces the stage it reached");
        // offline owner: nothing sent
        Env off = new Env();
        Kit.TestPlayer o3 = off.player("Owner", 0, 64, 0);
        EnderDragon d3 = off.tame(off.baby(0, 64, 0), o3, 0.0);
        off.svc.scan();
        o3.online = false;
        off.grow(d3, 0.6);
        off.svc.scan();
        expect(o3.messages.isEmpty(), "an offline owner is not messaged");
        // wild: nothing
        Env w = new Env();
        EnderDragon wild = w.baby(0, 64, 0);
        w.svc.scan();
        wild.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(0.7);
        w.svc.scan();
        expect(w.svc.stageOf(wild) == DragonStage.FLEDGLING, "the wild one did grow");
        // it has no owner, so there is nobody to tell
        // a release forgets the stage it was last seen at, so the next owner is not told about growth they did not watch
        Env r = new Env();
        Kit.TestPlayer first = r.player("First", 0, 64, 0);
        Kit.TestPlayer second = r.player("Second", 4, 64, 0);
        EnderDragon shared = r.tame(r.baby(0, 64, 0), first, 0.0);
        r.svc.scan();
        r.manager.release(shared);
        r.grow(shared, 1.0);
        r.manager.tame(shared, second.getUniqueId());
        r.grow(shared, 1.0);
        second.messages.clear();
        r.svc.scan();
        expect(second.messages.isEmpty(), "a new owner is not told what the last stage was: " + second.messages);
    }

    static void aura() {
        Env e = new Env();
        EnderDragon d = e.baby(0, 64, 0);
        e.svc.setColor(d, DragonColor.SAPPHIRE);
        e.svc.aura();
        expect(e.world.stubParticles.contains(Particle.DUST), "the aura is dust");
        Object data = e.world.stubParticleData.get(e.world.stubParticleData.size() - 1);
        expect(data instanceof Particle.DustOptions, "with a colour payload");
        if (data instanceof Particle.DustOptions dust) {
            expect(dust.getColor().equals(org.bukkit.Color.fromRGB(DragonColor.SAPPHIRE.red(), DragonColor.SAPPHIRE.green(), DragonColor.SAPPHIRE.blue())),
                    "in the dragon's colour");
        }
        e.world.stubParticles.clear();
        e.cfg.aura = false;
        e.svc.aura();
        expect(e.world.stubParticles.isEmpty(), "no dust when the aura is off");
        e.cfg.aura = true;
        d.remove();
        e.svc.aura();
        expect(e.world.stubParticles.isEmpty(), "and none for a dragon that is gone");
        Env n = new Env();
        n.svc.adopt(new EnderDragon(), null);
        n.svc.aura();
        expect(n.world.stubParticles.isEmpty(), "none for a dragon with no position");
    }

    static void refreshing() {
        Env e = new Env();
        EnderDragon d = e.baby(0, 64, 0);
        d.setAI(true);
        d.setPhase(EnderDragon.Phase.CIRCLING);
        e.cfg.glow = false;
        e.svc.refresh();
        expect(!d.hasAI(), "refresh switches the AI back off");
        expect(d.getPhase() == EnderDragon.Phase.HOVER, "and the phase back to hover");
        expect(!d.isGlowing(), "and applies the new glow setting");
        e.cfg.glow = true;
        d.remove();
        e.svc.refresh();
    }

    static void describing() {
        Env e = new Env();
        Kit.TestPlayer owner = e.player("Owner", 0, 64, 0);
        EnderDragon d = e.tame(e.baby(12, 70, -3), owner, 0.5);
        String line = e.svc.describe(d).replaceAll("\u00a7.", "");
        DragonColor c = e.svc.colorOf(d);
        expect(line.contains(c.display()), "names the colour: " + line);
        expect(line.contains("fledgling"), "names the stage: " + line);
        expect(line.contains("50% grown"), "gives the growth: " + line);
        expect(line.contains("12, 70, -3"), "gives the place: " + line);
        expect(line.contains("word " + e.svc.wordOf(d)), "gives the word: " + line);
        EnderDragon wild = e.baby(0, 64, 0);
        expect(!e.svc.describe(wild).contains("word"), "a wild one has no word to show");
        expect(e.svc.describe(new EnderDragon()).contains("?"), "no place shows a question mark");
    }

    /** Things the mutation pass showed the other checks were not pinning down. */
    static void edges() {
        // wild babies mostly stand still, and one that sets off keeps going
        Env w = new Env();
        List<EnderDragon> babies = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            babies.add(w.baby(-58 + i * 3, 64, 0));
        }
        double[] before = new double[40];
        for (int i = 0; i < 40; i++) {
            before[i] = babies.get(i).getLocation().getZ() * 1000 + babies.get(i).getLocation().getX();
        }
        w.svc.tick();
        boolean[] first = new boolean[40];
        int firstCount = 0;
        double[] mid = new double[40];
        for (int i = 0; i < 40; i++) {
            mid[i] = babies.get(i).getLocation().getZ() * 1000 + babies.get(i).getLocation().getX();
            first[i] = Math.abs(mid[i] - before[i]) > 1e-9;
            firstCount += first[i] ? 1 : 0;
        }
        w.svc.tick();
        int kept = 0;
        for (int i = 0; i < 40; i++) {
            double now = babies.get(i).getLocation().getZ() * 1000 + babies.get(i).getLocation().getX();
            if (first[i] && Math.abs(now - mid[i]) > 1e-9) {
                kept++;
            }
        }
        expect(firstCount >= 1 && firstCount <= 10, "about one in twenty wild babies sets off on a given tick: " + firstCount + " of 40");
        expect(kept * 2 >= firstCount, "and those that did keep walking: " + kept + " of " + firstCount);

        // exactly at the follow radius it stays; exactly at the teleport distance it still walks
        Env f = new Env();
        Kit.TestPlayer o = f.player("Owner", 6, 64, 0);
        EnderDragon d = f.tame(f.baby(0, 64, 0), o, 0.0);
        f.tick(5);
        near(d.getLocation().getX(), 0, 1e-9, "an owner exactly 6 blocks away is close enough");
        o.place(f.world, 6.5, 64, 0, 0f, 0f);
        f.tick(1);
        expect(d.getLocation().getX() > 0.1, "a little further and it walks");
        Env t = new Env();
        Kit.TestPlayer o2 = t.player("Owner", 32, 64, 0);
        EnderDragon d2 = t.tame(t.baby(0, 64, 0), o2, 0.0);
        t.tick(1);
        near(d2.getLocation().getX(), 0.3, 1e-6, "exactly 32 blocks away it walks rather than jumping");
        o2.place(t.world, 32.5, 64, 0, 0f, 0f);
        t.tick(1);
        near(d2.getLocation().getX(), 32.5, 1e-9, "further than that it is brought over");

        // a rider who left the server: the seat is emptied and nothing throws
        Env r = new Env();
        Kit.TestPlayer rider = r.player("Rider", 0, 64, 0);
        EnderDragon mount = r.fledgling(rider, 0, 64, 0);
        r.svc.mount(rider, mount);
        Bukkit.PLAYERS.remove(rider.getUniqueId());
        r.tick(2);
        expect(!r.svc.isRidden(mount), "a rider who is gone no longer holds the seat");

        // an order that ends does not also get one more move
        Env a = new Env();
        Kit.TestPlayer owner = a.player("Owner", 0, 64, 0);
        a.player("Prey", 20, 64, 0);
        EnderDragon fighter = a.adult(owner, 3, 64, 0);
        a.svc.attack(owner, fighter, Targeting.Mode.NEAREST, null);
        owner.online = false;
        Location parked = fighter.getLocation().clone();
        a.tick(1);
        near(fighter.getLocation().distance(parked), 0.0, 1e-9, "the tick that ends a fight does not carry on towards the target");

        // while it fights it is not also drawn back to its owner
        Env b = new Env();
        Kit.TestPlayer own = b.player("Owner", 0, 64, 0);
        Kit.TestPlayer prey = b.player("Prey", 40, 64, 0);
        EnderDragon hawk = b.adult(own, 3, 64, 0);
        b.svc.attack(own, hawk, Targeting.Mode.NEAREST, null);
        b.tick(60);
        double over = Math.hypot(hawk.getLocation().getX() - prey.getLocation().getX(), hawk.getLocation().getZ() - prey.getLocation().getZ());
        expect(b.svc.isAttacking(hawk) && over < 3.0, "a fighting dragon hangs over its target, not halfway home: " + over);
    }

    static class Exploding extends EnderDragon {
        @Override
        public Location getLocation() {
            throw new IllegalStateException("boom");
        }
    }

    static void robustness() {
        Env e = new Env();
        e.svc.adopt(new Exploding(), null);
        Kit.TestPlayer owner = e.player("Owner", 30, 64, 0);
        EnderDragon d = e.tame(e.baby(0, 64, 0), owner, 0.0);
        e.tick(1);
        expect(d.getLocation().getX() > 0.2, "one dragon failing does not stop the others");
        EnderDragon exploding = new Exploding();
        exploding.getPersistentDataContainer().set(e.keys.dragonMark, HatchlingKeys.INTEGER, 1);
        // an exception while scanning-free ticking is logged, not thrown
        java.util.logging.Logger log = e.plugin.getLogger();
        final java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.logging.Handler h = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { lines.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.addHandler(h);
        e.tick(1);
        log.removeHandler(h);
        boolean said = false;
        for (String l : lines) {
            said |= l.contains("could not be moved");
        }
        expect(said, "the failure is reported in the log");
        Env.TROUBLE.clear(); // those failures were the point of this test
    }
}
