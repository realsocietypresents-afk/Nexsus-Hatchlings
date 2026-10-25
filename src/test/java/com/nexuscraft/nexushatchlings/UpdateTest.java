package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexushatchlings.Kit.expect;
import static com.nexuscraft.nexushatchlings.Kit.near;

/** The 1.5 changes to how dragons are kept: the limit per player, staying and following, and standing apart. */
public class UpdateTest {

    public static void main(String[] args) {
        settings();
        rosterFile();
        limit();
        limitEdges();
        staying();
        stayingEdges();
        stayingTogether();
        formation();
        formationEdges();
        commands();
        Kit.finish();
    }

    // ------------------------------------------------------------------ helpers

    static double flat(Entity a, Entity b) {
        return Math.hypot(a.getLocation().getX() - b.getLocation().getX(), a.getLocation().getZ() - b.getLocation().getZ());
    }

    static double flatTo(Entity a, double x, double z) {
        return Math.hypot(a.getLocation().getX() - x, a.getLocation().getZ() - z);
    }

    static double closest(List<EnderDragon> ds) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < ds.size(); i++) {
            for (int j = i + 1; j < ds.size(); j++) {
                best = Math.min(best, flat(ds.get(i), ds.get(j)));
            }
        }
        return best;
    }

    /** An env with the roster switched on, the way the plugin runs. */
    static Env withRoster() {
        Env e = new Env();
        e.svc.setRoster(new DragonRoster(e.dir, e.warnings::add, e.clock::get));
        return e;
    }

    static CrystalTaming taming(Env e) {
        CrystalTaming t = new CrystalTaming(e.hatch, e.manager, new CrystalItem(e.keys));
        t.setDragons(e.svc);
        return t;
    }

    static int crystalsIn(Env e, Kit.TestPlayer p) {
        CrystalItem crystal = new CrystalItem(e.keys);
        int n = 0;
        for (int i = 0; i < 36; i++) {
            org.bukkit.inventory.ItemStack st = p.getInventory().getItem(i);
            if (st != null && crystal.isCrystal(st)) {
                n += st.getAmount();
            }
        }
        return n;
    }

    static List<EnderDragon> adults(Env e, Kit.TestPlayer owner, int n, double x, double z) {
        List<EnderDragon> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(e.adult(owner, x, 64, z));
        }
        return out;
    }

    // ------------------------------------------------------------------ the new settings

    static void settings() {
        DragonConfig c = new DragonConfig();
        List<String> warnings = new ArrayList<>();
        c.load(null, warnings::add);
        expect(warnings.isEmpty(), "defaults need no correction");
        expect(c.maxPerPlayer == 10, "the limit is ten");
        expect(c.staleDays == 30, "an unseen dragon stops counting after 30 days");
        expect(c.formation && c.formationSpacing == 6.0 && c.formationMinRadius == 4.0 && c.formationTolerance == 2.0, "formation defaults");
        expect(c.model && c.modelSize == 1.0 && c.modelUpdateTicks == 4 && c.modelViewRange == 1.5, "model defaults");

        Kit.MapSection s = new Kit.MapSection();
        s.set("dragons.limit.max-per-player", 3);
        s.set("dragons.limit.forget-after-days", 7);
        s.set("dragons.formation.enabled", false);
        s.set("dragons.formation.spacing", 9.5);
        s.set("dragons.formation.min-radius", 2.5);
        s.set("dragons.formation.tolerance", 1.0);
        s.set("dragons.look.model.enabled", false);
        s.set("dragons.look.model.size", 2.0);
        s.set("dragons.look.model.update-ticks", 6);
        s.set("dragons.look.model.view-range", 3.0);
        warnings.clear();
        c.load(s, warnings::add);
        expect(warnings.isEmpty(), "good values are taken as they are: " + warnings);
        expect(c.maxPerPlayer == 3 && c.staleDays == 7, "limit read");
        expect(!c.formation && c.formationSpacing == 9.5 && c.formationMinRadius == 2.5 && c.formationTolerance == 1.0, "formation read");
        expect(!c.model && c.modelSize == 2.0 && c.modelUpdateTicks == 6 && c.modelViewRange == 3.0, "model read");

        Kit.MapSection bad = new Kit.MapSection();
        bad.set("dragons.limit.max-per-player", 0);
        bad.set("dragons.limit.forget-after-days", 9999);
        bad.set("dragons.formation.spacing", 0.0);
        bad.set("dragons.formation.min-radius", -4);
        bad.set("dragons.formation.tolerance", 99);
        bad.set("dragons.look.model.size", 50);
        bad.set("dragons.look.model.update-ticks", 0);
        bad.set("dragons.look.model.view-range", 0);
        warnings.clear();
        c.load(bad, warnings::add);
        expect(warnings.size() == 8, "every wild value is reported (" + warnings.size() + "): " + warnings);
        expect(c.maxPerPlayer == 1 && c.staleDays == 365, "limit moved into range");
        expect(c.formationSpacing == 1.0 && c.formationMinRadius == 0.0 && c.formationTolerance == 10.0, "formation moved into range");
        expect(c.modelSize == 4.0 && c.modelUpdateTicks == 1 && c.modelViewRange == 0.5, "model moved into range");
        expect(CommandWords.RESERVED.containsAll(List.of("stay", "follow", "come", "menu", "release")), "the new sub-commands cannot be used as words");
        expect(!CommandWords.valid("stay") && !CommandWords.valid("menu") && !CommandWords.valid("release"), "and are refused as words");
    }

    // ------------------------------------------------------------------ the roster file

    static void rosterFile() {
        File dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("roster").toFile();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        long[] now = {1_000_000_000L};
        List<String> warnings = new ArrayList<>();
        DragonRoster r = new DragonRoster(dir, warnings::add, () -> now[0]);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        UUID d3 = UUID.randomUUID();
        expect(r.size() == 0 && r.count(alice) == 0, "starts empty");
        r.add(alice, d1);
        r.add(alice, d2);
        r.add(bob, d3);
        expect(r.count(alice) == 2 && r.count(bob) == 1 && r.size() == 3, "counts per owner");
        expect(r.has(d1) && r.ownerOf(d1).equals(alice) && r.ownerOf(UUID.randomUUID()) == null, "who owns what");
        r.add(alice, d1);
        expect(r.count(alice) == 2, "adding twice does not count twice");
        r.add(bob, d1);
        expect(r.count(alice) == 1 && r.count(bob) == 2 && r.ownerOf(d1).equals(bob), "a dragon changing hands moves across");
        r.remove(d1);
        r.remove(UUID.randomUUID());
        expect(r.count(bob) == 1 && !r.has(d1), "removing frees the place");
        r.add(alice, d1);

        r.flush();
        File file = new File(dir, "roster.tsv");
        expect(file.isFile(), "the roster is written");
        DragonRoster again = new DragonRoster(dir, warnings::add, () -> now[0]);
        again.load();
        expect(again.count(alice) == 2 && again.count(bob) == 1 && again.has(d3), "and read back the same");

        // time passes: seen dragons stay, unseen ones go
        now[0] += 40L * 86_400_000L;
        again.seen(d1);
        again.seen(UUID.randomUUID());
        expect(again.prune(30L * 86_400_000L) == 2, "two dragons were not seen for 40 days");
        expect(again.has(d1) && !again.has(d2) && !again.has(d3), "only the one seen lately is kept");
        expect(again.prune(30L * 86_400_000L) == 0, "nothing more to drop");

        // small time steps do not make the file dirty
        File f2 = new File(dir, "roster.tsv");
        long before = f2.lastModified();
        again.flush();
        again.seen(d1);
        again.flush();
        expect(f2.lastModified() >= before, "flushing is harmless");

        // damaged lines are skipped with a warning, good ones kept
        try {
            java.nio.file.Files.writeString(file.toPath(), "# header\n" + d1 + "\t" + alice + "\t5\nnot a line\n" + d2 + "\tnot-a-uuid\t1\n\n");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        warnings.clear();
        DragonRoster damaged = new DragonRoster(dir, warnings::add, () -> now[0]);
        damaged.load();
        expect(damaged.size() == 1 && damaged.has(d1), "the readable line survives");
        expect(warnings.size() == 2, "and the two bad lines are reported: " + warnings);
        expect(new DragonRoster(new File(dir, "nowhere"), warnings::add, () -> now[0]).size() == 0, "a missing file is an empty roster");
    }

    // ------------------------------------------------------------------ the limit

    static void limit() {
        Env e = withRoster();
        e.cfg.maxPerPlayer = 3;
        CrystalTaming taming = taming(e);
        Kit.TestPlayer tamer = e.player("Tamer", 0, 64, 0);
        Kit.TestPlayer other = e.player("Other", 40, 64, 0);

        List<EnderDragon> kept = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            EnderDragon d = e.baby(2 + i, 64, 0);
            taming.tame(d, tamer.getUniqueId(), d.getLocation());
            expect(e.manager.isTame(d), "dragon " + (i + 1) + " is tamed");
            kept.add(d);
        }
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 3, "three owned");
        expect(e.svc.roster().count(tamer.getUniqueId()) == 3, "and on the roster");

        EnderDragon fourth = e.baby(8, 64, 0);
        int crystalsBefore = crystalsIn(e, tamer);
        taming.tame(fourth, tamer.getUniqueId(), fourth.getLocation());
        expect(!e.manager.isTame(fourth), "the fourth is refused");
        expect(e.manager.ownerOf(fourth) == null && e.svc.wordOf(fourth) == null, "and stays wild");
        expect(crystalsIn(e, tamer) == crystalsBefore + 1, "the crystal came back");
        expect(tamer.heard("most one player can have") && tamer.heard("(3)") && tamer.heard("/dragon menu"), "and the player is told why: " + tamer.messages);
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 3, "still three");

        // another player is not affected by the first one's dragons
        EnderDragon theirs = e.baby(42, 64, 0);
        taming.tame(theirs, other.getUniqueId(), theirs.getLocation());
        expect(e.manager.isTame(theirs), "the limit is per player");

        // a mob that is not a dragon is not counted against it
        Zombie zombie = e.world.spawn(e.at(10, 64, 0), Zombie.class);
        taming.tame(zombie, tamer.getUniqueId(), zombie.getLocation());
        expect(e.manager.isTame(zombie), "other creatures are not limited");
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 3, "and do not count");

        // letting one go makes room
        expect(e.svc.release(tamer, kept.get(0)), "a dragon can be let go");
        expect(!e.manager.isTame(kept.get(0)) && e.svc.wordOf(kept.get(0)) == null, "it is wild now");
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 2, "two owned");
        taming.tame(fourth, tamer.getUniqueId(), fourth.getLocation());
        expect(e.manager.isTame(fourth), "so another can be tamed");
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 3, "back to three");

        // a dead dragon makes room too
        EnderDragon doomed = kept.get(1);
        doomed.remove();
        e.svc.onDeath(doomed);
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 2, "death frees a place");
        DragonListener listener = new DragonListener(e.cfg, e.svc, e.eggs);
        EnderDragon doomed2 = kept.get(2);
        listener.onDeath(new EntityDeathEvent(doomed2));
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 1, "and so does the death event");
        expect(!e.svc.roster().has(doomed2.getUniqueId()), "the roster forgot it");
        expect(!e.svc.loaded().contains(doomed2), "and the service stopped moving it");
        // a dragon takes ten seconds to die and stays in the world meanwhile: the scan must not take it up again
        doomed2.stubSetDead(true);
        e.svc.scan();
        e.tick(3);
        expect(!e.svc.loaded().contains(doomed2) && !e.svc.roster().has(doomed2.getUniqueId()), "a dying dragon is not picked up again");
        expect(e.svc.ownedCount(tamer.getUniqueId()) == 1, "and does not use up a place");
        // one that starts dying without the death event reaching us is dropped by the scan
        EnderDragon dying = e.adult(tamer, 20, 64, 0);
        expect(e.svc.loaded().contains(dying), "alive: moved by the service");
        dying.stubSetDead(true);
        e.svc.scan();
        expect(!e.svc.loaded().contains(dying), "a dragon found dying is let go of");
        expect(!e.svc.roster().has(dying.getUniqueId()), "and no longer counts");
        // someone else cannot let a dragon go for you
        expect(!e.svc.release(other, fourth), "only the owner can release");
        expect(e.manager.isTame(fourth), "and it stays tamed");
        expect(!e.svc.release(tamer, theirs), "and you cannot release another player's");
    }

    static void limitEdges() {
        // sleeping dragons count
        Env e = withRoster();
        e.cfg.maxPerPlayer = 2;
        CrystalTaming taming = taming(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        e.svc.roster().add(p.getUniqueId(), UUID.randomUUID());
        e.svc.roster().add(p.getUniqueId(), UUID.randomUUID());
        expect(e.svc.dragonsOf(p.getUniqueId()).isEmpty() && e.svc.ownedCount(p.getUniqueId()) == 2, "two asleep elsewhere count though none is loaded");
        EnderDragon d = e.baby(3, 64, 0);
        taming.tame(d, p.getUniqueId(), d.getLocation());
        expect(!e.manager.isTame(d), "so a third is refused");

        // staff with the permission are not limited
        p.perms.add("nexushatchlings.dragons.unlimited");
        taming.tame(d, p.getUniqueId(), d.getLocation());
        expect(e.manager.isTame(d), "the unlimited permission lifts it");
        expect(e.svc.ownedCount(p.getUniqueId()) == 3, "and the dragon is counted");

        // a player who is offline still cannot go over
        Env off = withRoster();
        off.cfg.maxPerPlayer = 1;
        UUID ghost = UUID.randomUUID();
        off.svc.roster().add(ghost, UUID.randomUUID());
        EnderDragon wild = off.baby(0, 64, 0);
        taming(off).tame(wild, ghost, wild.getLocation());
        expect(!off.manager.isTame(wild), "an offline owner is held to the limit too");
        expect(!off.world.stubDrops.isEmpty(), "and the crystal is dropped where it landed");

        // without a roster the loaded dragons are what count
        Env bare = new Env();
        bare.cfg.maxPerPlayer = 1;
        Kit.TestPlayer q = bare.player("Q", 0, 64, 0);
        bare.adult(q, 1, 64, 0);
        expect(bare.svc.ownedCount(q.getUniqueId()) == 1 && !bare.svc.mayTameAnother(q.getUniqueId()), "no roster: loaded dragons count");
        expect(bare.svc.roster() == null, "no roster by default in the test env");

        // dragons that were tamed before the limit existed keep working
        Env old = withRoster();
        old.cfg.maxPerPlayer = 2;
        Kit.TestPlayer vet = old.player("Vet", 0, 64, 0);
        List<EnderDragon> pack = adults(old, vet, 5, 1, 1);
        old.svc.scan();
        expect(old.svc.dragonsOf(vet.getUniqueId()).size() == 5, "five old dragons are all still theirs");
        expect(old.svc.ownedCount(vet.getUniqueId()) == 5, "and all five count");
        expect(!old.svc.mayTameAnother(vet.getUniqueId()), "but no more can be added");
        for (EnderDragon d2 : pack) {
            expect(old.manager.isTame(d2), "none was taken away");
        }

        // the scan keeps the roster honest: it picks up dragons it did not know, and forgets long-unseen ones
        Env scan = withRoster();
        Kit.TestPlayer s = scan.player("S", 0, 64, 0);
        EnderDragon known = scan.adult(s, 1, 64, 1);
        scan.svc.roster().remove(known.getUniqueId());
        scan.svc.scan();
        expect(scan.svc.roster().has(known.getUniqueId()), "a tamed dragon missing from the roster is added by the scan");
        UUID gone = UUID.randomUUID();
        scan.svc.roster().add(s.getUniqueId(), gone);
        scan.clock.addAndGet(31L * 86_400_000L);
        scan.svc.scan();
        expect(!scan.svc.roster().has(gone), "a dragon unseen for over 30 days drops off");
        expect(scan.svc.roster().has(known.getUniqueId()), "a dragon seen in that scan stays");
        expect(new File(scan.dir, "roster.tsv").isFile(), "and the scan saved the roster");

        // shutdown saves
        Env shut = withRoster();
        Kit.TestPlayer t = shut.player("T", 0, 64, 0);
        shut.adult(t, 1, 64, 1);
        shut.svc.shutdown();
        expect(new File(shut.dir, "roster.tsv").isFile(), "switching off writes the roster");
        new Env().svc.shutdown();
    }

    // ------------------------------------------------------------------ staying and following

    static void staying() {
        Env e = new Env();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon d = e.adult(o, 8, 64, 0);
        expect(!e.svc.isStaying(d) && e.svc.statusOf(d).equals("Following"), "a tamed dragon follows by default");
        expect(e.svc.stay(o, d, false) == DragonService.StayResult.OK, "told to stay where it is");
        expect(e.svc.isStaying(d) && e.svc.statusOf(d).equals("Staying"), "now staying");
        DragonService.Spot spot = e.svc.stayOf(d);
        near(spot.x(), 8.0, 1e-9, "the spot is where the dragon was (x)");
        near(spot.y(), 64.0, 1e-9, "the spot is where the dragon was (y)");
        expect(spot.world().equals("world"), "and in its world");
        expect(e.svc.describe(d).contains("staying"), "the description says so: " + e.svc.describe(d));

        // the owner wanders far away, even out of teleport range: the dragon does not budge
        o.place(e.world, 50, 64, 0, 0f, 0f);
        e.tick(120);
        near(d.getLocation().getX(), 8.0, 0.6, "it stays when its owner walks off");
        o.place(e.world, 500, 64, 0, 0f, 0f);
        e.tick(120);
        near(d.getLocation().getX(), 8.0, 0.6, "and does not teleport to an owner who is miles away");
        o.place(e.world, 0, 64, 0, 0f, 0f);

        // follow brings it back
        expect(e.svc.follow(d), "told to follow: it had been staying");
        expect(!e.svc.isStaying(d) && e.svc.stayOf(d) == null, "no longer staying");
        expect(!d.getPersistentDataContainer().has(e.keys.dragonStay, HatchlingKeys.STRING), "and nothing saved on it");
        expect(!e.svc.follow(d), "telling it again changes nothing");
        o.place(e.world, -20, 64, 0, 0f, 0f);
        e.tick(200);
        expect(flat(d, o) <= e.hatch.followRadiusBlocks() + 1.0, "it comes after its owner again (" + flat(d, o) + ")");

        // stay at the owner's spot: the dragon comes and waits there
        o.place(e.world, 20, 64, 5, 0f, 0f);
        expect(e.svc.stay(o, d, true) == DragonService.StayResult.OK, "told to stay at the owner's spot");
        e.tick(200);
        expect(flatTo(d, 20, 5) < 1.5, "it waits where the owner stood (" + d.getLocation() + ")");
        o.place(e.world, 25, 64, 25, 0f, 0f);
        e.tick(60);
        expect(flatTo(d, 20, 5) < 1.5, "and stays there when the owner leaves");

        // it is remembered on the dragon itself, so a restart or a chunk reload keeps it
        String saved = d.getPersistentDataContainer().get(e.keys.dragonStay, HatchlingKeys.STRING);
        expect(saved != null && saved.startsWith("world|"), "the spot is saved on the dragon: " + saved);
        DragonService fresh = new DragonService(e.plugin, e.hatch, e.cfg, e.keys, e.manager, e.houses, e.random);
        expect(!fresh.isStaying(d), "a new service knows nothing yet");
        fresh.adopt(d, o.getUniqueId());
        expect(fresh.isStaying(d), "but picking the dragon up again brings the order back");
        near(fresh.stayOf(d).x(), e.svc.stayOf(d).x(), 1e-9, "at the same spot");

        // riding ends a wait
        Env r = new Env();
        Kit.TestPlayer rider = r.player("Rider", 0, 64, 0);
        EnderDragon steed = r.adult(rider, 3, 64, 0);
        r.svc.stay(rider, steed, false);
        expect(r.svc.mount(rider, steed) == DragonService.MountResult.OK, "climbing on");
        expect(!r.svc.isStaying(steed), "ends the wait");
        expect(!steed.getPersistentDataContainer().has(r.keys.dragonStay, HatchlingKeys.STRING), "and forgets the spot");
        expect(r.svc.statusOf(steed).equals("Carrying a rider"), "status while ridden");
        expect(r.svc.stay(rider, steed, false) == DragonService.StayResult.RIDDEN, "a ridden dragon cannot be told to stay");
        expect(r.svc.stay(rider, steed, true) == DragonService.StayResult.RIDDEN, "not even at the rider's spot");

        // a baby that cannot fly walks to its spot and waits
        Env w = new Env();
        Kit.TestPlayer owner = w.player("W", 0, 64, 10);
        EnderDragon baby = w.tame(w.baby(5, 64, 0), owner, 0.0);
        expect(!w.svc.stageOf(baby).canFly(), "a young dragon walks");
        w.svc.stay(owner, baby, true);
        w.tick(240);
        expect(flatTo(baby, 0, 10) < 1.5, "it walks to its spot (" + baby.getLocation() + ")");
        near(baby.getLocation().getY(), 64.0, 0.01, "on the ground");
        owner.place(w.world, 0, 64, 40, 0f, 0f);
        w.tick(120);
        expect(flatTo(baby, 0, 10) < 1.5, "and waits");
    }

    static void stayingEdges() {
        Env e = new Env();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        Kit.TestPlayer stranger = e.player("Stranger", 5, 64, 0);
        EnderDragon d = e.adult(o, 6, 64, 0);
        EnderDragon wild = e.baby(9, 64, 0);
        expect(e.svc.stay(stranger, d, false) == DragonService.StayResult.NOT_OWNER, "only the owner can tell it to stay");
        expect(e.svc.stay(o, wild, false) == DragonService.StayResult.NOT_OWNER, "a wild dragon cannot be told to");
        expect(!e.svc.isStaying(d) && !e.svc.isStaying(wild), "nothing changed");
        expect(e.svc.statusOf(wild).equals("Wild"), "wild status");

        // in another world, "at my spot" cannot work but "where it is" can
        World nether = new World("nether");
        Bukkit.WORLDS.put("nether", nether);
        EnderDragon far = e.svc.spawnBaby(new Location(nether, 0, 64, 0));
        e.tame(far, o, 1.0);
        expect(e.svc.stay(o, far, true) == DragonService.StayResult.WRONG_WORLD, "a dragon in another world cannot come to your spot");
        expect(!e.svc.isStaying(far), "and is not left half-told");
        expect(e.svc.stay(o, far, false) == DragonService.StayResult.OK, "but can wait where it is");
        expect(e.svc.stayOf(far).world().equals("nether"), "in its own world");
        // a dragon whose spot is in a world it is no longer in goes back to following
        far.stubSetLocation(new Location(e.world, 0, 64, 0));
        e.tick(2);
        expect(!e.svc.isStaying(far), "a dragon found outside its waiting world is let go to follow");

        // the stay order survives an attack and resumes
        Env a = new Env();
        Kit.TestPlayer owner = a.player("Owner", 0, 64, 0);
        Kit.TestPlayer foe = a.player("Foe", 30, 64, 0);
        foe.setGameMode(GameMode.SURVIVAL);
        EnderDragon fighter = a.adult(owner, 4, 64, 0);
        a.svc.stay(owner, fighter, false);
        DragonService.OrderResult order = a.svc.attack(owner, fighter, Targeting.Mode.NEAREST, null);
        expect(order.status() == DragonService.Status.STARTED, "a waiting dragon can still be sent to fight");
        a.tick(60);
        expect(flat(fighter, foe) < 12, "it goes after the foe");
        expect(a.svc.statusOf(fighter).equals("Attacking"), "status while fighting");
        expect(a.svc.stop(fighter), "called off");
        a.tick(300);
        expect(flatTo(fighter, 4, 0) < 1.5, "it goes back to its spot afterwards (" + fighter.getLocation() + ")");
        expect(a.svc.isStaying(fighter), "still staying");

        // letting a dragon go forgets the wait
        Env g = new Env();
        Kit.TestPlayer og = g.player("G", 0, 64, 0);
        EnderDragon pet = g.adult(og, 4, 64, 0);
        g.svc.stay(og, pet, false);
        expect(g.svc.release(og, pet), "released");
        expect(!g.svc.isStaying(pet), "a wild dragon is not waiting");
        expect(!pet.getPersistentDataContainer().has(g.keys.dragonStay, HatchlingKeys.STRING), "and nothing is left on it");
        // a stale spot found on a dragon with no owner is dropped when it is picked up
        pet.getPersistentDataContainer().set(g.keys.dragonStay, HatchlingKeys.STRING, "world|1.0|64.0|1.0");
        g.svc.adopt(pet, null);
        expect(!g.svc.isStaying(pet), "a wild dragon ignores a leftover wait");
        expect(!pet.getPersistentDataContainer().has(g.keys.dragonStay, HatchlingKeys.STRING), "and the leftover is wiped");

        // the spot text
        DragonService.Spot s = new DragonService.Spot("my|world", 1.5, 64.0, -3.25);
        DragonService.Spot back = DragonService.Spot.decode(s.encode());
        expect(back != null && back.equals(s), "a spot survives being written down, even with a bar in the world name: " + s.encode());
        expect(DragonService.Spot.decode(null) == null && DragonService.Spot.decode("") == null, "nothing decodes to nothing");
        expect(DragonService.Spot.decode("world|1|2") == null && DragonService.Spot.decode("world|a|b|c") == null, "damaged text decodes to nothing");

        // a waiting dragon with its owner offline stays put
        Env off = new Env();
        Kit.TestPlayer so = off.player("Sleepy", 0, 64, 0);
        EnderDragon sleeper = off.adult(so, 5, 64, 5);
        off.svc.stay(so, sleeper, false);
        so.online = false;
        off.tick(100);
        near(sleeper.getLocation().getX(), 5.0, 0.6, "it waits for an owner who logged out");
    }

    static void stayingTogether() {
        Env e = new Env();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        List<EnderDragon> pack = adults(e, o, 4, 10, 10);
        Kit.TestPlayer stranger = e.player("Stranger", 30, 64, 30);
        EnderDragon notMine = e.adult(stranger, 12, 64, 12);
        expect(e.svc.stayAll(o, true) == 4, "four dragons told to gather and wait");
        expect(!e.svc.isStaying(notMine), "someone else's dragon is not affected");
        e.tick(250);
        expect(closest(pack) >= e.cfg.formationSpacing - 1.0, "they wait spread out, not on top of each other (" + closest(pack) + ")");
        for (EnderDragon d : pack) {
            expect(flat(d, o) <= 8.0 && flat(d, o) >= 2.0, "each waits around its owner, not at the owner's feet (" + flat(d, o) + ")");
            expect(e.svc.isStaying(d), "and stays");
        }
        double[] where = new double[pack.size()];
        for (int i = 0; i < pack.size(); i++) {
            where[i] = pack.get(i).getLocation().getX();
        }
        o.place(e.world, 50, 64, 50, 0f, 0f);
        e.tick(100);
        for (int i = 0; i < pack.size(); i++) {
            near(pack.get(i).getLocation().getX(), where[i], 0.6, "they do not follow when their owner leaves");
        }
        expect(e.svc.followAll(o.getUniqueId()) == 4, "all four told to follow");
        expect(e.svc.followAll(o.getUniqueId()) == 0, "and none is left waiting");
        for (EnderDragon d : pack) {
            expect(!e.svc.isStaying(d), "following again");
        }

        // stay where each is
        expect(e.svc.stayAll(o, false) == 4, "all stay where they are");
        for (EnderDragon d : pack) {
            near(e.svc.stayOf(d).x(), d.getLocation().getX(), 1e-9, "each at its own spot");
        }
        e.svc.followAll(o.getUniqueId());

        // one dragon sent to the owner's spot is put clear of one already waiting there
        Env c = new Env();
        Kit.TestPlayer co = c.player("C", 0, 64, 0);
        EnderDragon first = c.adult(co, 10, 64, 0);
        EnderDragon second = c.adult(co, 0, 64, 10);
        c.svc.stay(co, first, true);
        c.svc.stay(co, second, true);
        DragonService.Spot a = c.svc.stayOf(first);
        DragonService.Spot b = c.svc.stayOf(second);
        expect(Math.hypot(a.x() - b.x(), a.z() - b.z()) >= c.cfg.formationSpacing - 1e-9, "the second waits clear of the first ("
                + Math.hypot(a.x() - b.x(), a.z() - b.z()) + ")");
        // and a dragon told twice does not push itself away
        c.svc.stay(co, first, true);
        near(c.svc.stayOf(first).x(), a.x(), 1e-9, "telling the same dragon again keeps its place (x)");
        near(c.svc.stayOf(first).z(), a.z(), 1e-9, "telling the same dragon again keeps its place (z)");

        // nobody to tell
        Env n = new Env();
        Kit.TestPlayer lonely = n.player("Lonely", 0, 64, 0);
        expect(n.svc.stayAll(lonely, true) == 0 && n.svc.stayAll(lonely, false) == 0 && n.svc.followAll(lonely.getUniqueId()) == 0, "no dragons, nothing to do");
        // a ridden dragon is left alone by "all"
        Env rd = new Env();
        Kit.TestPlayer ro = rd.player("R", 0, 64, 0);
        EnderDragon ridden = rd.adult(ro, 2, 64, 0);
        EnderDragon spare = rd.adult(ro, 4, 64, 0);
        rd.svc.mount(ro, ridden);
        expect(rd.svc.stayAll(ro, true) == 1 && rd.svc.isStaying(spare) && !rd.svc.isStaying(ridden), "stay all skips the dragon being ridden");
    }

    // ------------------------------------------------------------------ standing apart

    static void formation() {
        Env e = new Env();
        e.cfg.formation = true;
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        List<EnderDragon> pack = adults(e, o, 5, 14, 0);
        expect(closest(pack) < 0.01, "they start on top of each other");
        e.tick(300);
        expect(closest(pack) >= 2.5, "following dragons spread out (closest " + closest(pack) + ")");
        double ring = Math.max(e.cfg.formationMinRadius, e.cfg.formationSpacing / (2.0 * Math.sin(Math.PI / 5)));
        for (EnderDragon d : pack) {
            expect(Math.abs(flat(d, o) - ring) <= e.cfg.formationTolerance + 0.75, "each stands about on the ring (" + flat(d, o) + " vs " + ring + ")");
        }
        // they hold their places when nothing changes
        List<Location> held = new ArrayList<>();
        for (EnderDragon d : pack) {
            held.add(d.getLocation().clone());
        }
        e.tick(100);
        for (int i = 0; i < pack.size(); i++) {
            expect(held.get(i).distance(pack.get(i).getLocation()) < 0.5, "a settled dragon keeps its place");
        }
        // the owner walks: the whole group moves with them and stays spread
        o.place(e.world, 20, 64, 0, 0f, 0f);
        e.tick(300);
        expect(closest(pack) >= 2.5, "still spread out after following (" + closest(pack) + ")");
        for (EnderDragon d : pack) {
            expect(flat(d, o) <= ring + e.cfg.formationTolerance + 1.0, "and still with the owner (" + flat(d, o) + ")");
        }

        // without formation the same crowd piles up
        Env flatEnv = new Env();
        Kit.TestPlayer fo = flatEnv.player("F", 0, 64, 0);
        List<EnderDragon> pile = adults(flatEnv, fo, 5, 14, 0);
        flatEnv.tick(300);
        expect(closest(pile) < 1.0, "with formation off they stack (" + closest(pile) + ")");

        // a lone dragon is not given a ring: it comes up to its owner as before
        Env one = new Env();
        one.cfg.formation = true;
        Kit.TestPlayer so = one.player("S", 0, 64, 0);
        EnderDragon lone = one.adult(so, 25, 64, 0);
        one.tick(300);
        expect(flat(lone, so) <= one.hatch.followRadiusBlocks() + 0.5 && flat(lone, so) >= 4.0, "a single dragon stops at the follow radius (" + flat(lone, so) + ")");
    }

    static void formationEdges() {
        Env e = new Env();
        e.cfg.formation = true;
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        List<EnderDragon> pack = adults(e, o, 3, 10, 0);
        e.svc.stay(o, pack.get(0), false);
        e.tick(250);
        List<EnderDragon> moving = pack.subList(1, 3);
        expect(Math.abs(flat(moving.get(0), moving.get(1)) - e.cfg.formationSpacing) <= 2 * e.cfg.formationTolerance + 0.5,
                "a waiting dragon is not counted: the other two share a ring of two (" + flat(moving.get(0), moving.get(1)) + ")");
        near(pack.get(0).getLocation().getX(), 10.0, 0.6, "and the waiting one has not moved");

        // when the owner is far away, dragons are brought to their own places, not all to the same one
        Env far = new Env();
        far.cfg.formation = true;
        Kit.TestPlayer fo = far.player("Far", 0, 64, 0);
        List<EnderDragon> lost = adults(far, fo, 4, 55, 55);
        far.tick(2);
        for (EnderDragon d : lost) {
            expect(flat(d, fo) < 12.0, "each was brought back to its owner (" + flat(d, fo) + ")");
        }
        expect(closest(lost) >= far.cfg.formationSpacing - 0.5, "to places that are apart (" + closest(lost) + ")");

        // young walkers spread out as well
        Env kids = new Env();
        kids.cfg.formation = true;
        Kit.TestPlayer ko = kids.player("K", 0, 64, 0);
        List<EnderDragon> babies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            babies.add(kids.tame(kids.baby(12, 64, 0), ko, 0.0));
        }
        kids.tick(600);
        expect(closest(babies) >= 2.0, "baby dragons spread out too (" + closest(babies) + ")");
        for (EnderDragon b : babies) {
            near(b.getLocation().getY(), 64.0, 0.01, "and walk on the ground");
        }

        // a ring spot inside a wall is not used for a teleport
        Env wall = new Env();
        wall.cfg.formation = true;
        Kit.TestPlayer wo = wall.player("W", 0, 64, 0);
        List<EnderDragon> kids2 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            kids2.add(wall.tame(wall.baby(100, 64, 100), wo, 0.0));
        }
        wall.tick(2);
        for (EnderDragon d : kids2) {
            expect(!Double.isNaN(d.getLocation().getY()) && d.getLocation().getY() >= 63.0, "brought back to standable ground");
        }

        // only the owner's own dragons make a ring, and only while the owner is there
        Env two = new Env();
        two.cfg.formation = true;
        Kit.TestPlayer a = two.player("A", 0, 64, 0);
        Kit.TestPlayer b = two.player("B", 40, 64, 40);
        EnderDragon da = two.adult(a, 12, 64, 0);
        EnderDragon db = two.adult(b, 44, 64, 40);
        two.tick(300);
        expect(flat(da, a) <= two.hatch.followRadiusBlocks() + 0.5, "one dragon each: each comes to its own owner (" + flat(da, a) + ")");
        expect(flat(db, b) <= two.hatch.followRadiusBlocks() + 0.5, "the other too (" + flat(db, b) + ")");
        a.online = false;
        two.tick(20);
        expect(da.isValid(), "a dragon whose owner logged out just stays");
        Entity unused = da;
        expect(unused != null, "no error");

        // a waiting dragon takes no place in the ring: the two that follow stand exactly opposite each other
        Env w = new Env();
        w.cfg.formation = true;
        Kit.TestPlayer waiter = w.player("Waiter", 0, 64, 0);
        List<EnderDragon> trio = adults(w, waiter, 3, 55, 55);
        w.svc.stay(waiter, trio.get(0), false);
        w.tick(2);
        double pair = 2 * Math.max(w.cfg.formationMinRadius, w.cfg.formationSpacing / (2.0 * Math.sin(Math.PI / 2)));
        near(flat(trio.get(1), trio.get(2)), pair, 0.3, "two followers share a ring of two, whatever a third is doing");
        near(trio.get(0).getLocation().getX(), 55.0, 0.01, "and the waiting one stayed put");

        // a dragon in another world is no part of the ring either
        Env x = new Env();
        x.cfg.formation = true;
        Kit.TestPlayer xo = x.player("Crosser", 0, 64, 0);
        List<EnderDragon> twin = adults(x, xo, 2, 55, 55);
        World nether = new World("nether");
        Bukkit.WORLDS.put("nether", nether);
        EnderDragon abroad = x.svc.spawnBaby(new Location(nether, 0, 64, 0));
        x.tame(abroad, xo, 1.0);
        x.tick(2);
        near(flat(twin.get(0), twin.get(1)), pair, 0.3, "the two here share a ring of two, not three");

        // a dragon that is close enough to its place is left where it is
        Env t = new Env();
        t.cfg.formation = true;
        Kit.TestPlayer tol = t.player("Tol", 0, 64, 0);
        EnderDragon first = t.adult(tol, 30, 64, 0);
        EnderDragon second = t.adult(tol, 30, 64, 0);
        List<java.util.UUID> ids = List.of(first.getUniqueId(), second.getUniqueId());
        t.tick(300);
        V3 slot = Formation.slotFor(ids, first.getUniqueId(), t.cfg.formationSpacing, t.cfg.formationMinRadius);
        double gx = tol.getLocation().getX() + slot.x();
        double gz = tol.getLocation().getZ() + slot.z();
        first.teleport(new Location(t.world, gx + 1.0, first.getLocation().getY(), gz));
        Location parked = first.getLocation().clone();
        t.tick(10);
        expect(flatTo(first, parked.getX(), parked.getZ()) < 0.3, "a dragon within the tolerance of its place does not shuffle (" + flatTo(first, parked.getX(), parked.getZ()) + ")");
        first.teleport(new Location(t.world, gx + t.cfg.formationTolerance + 2.0, first.getLocation().getY(), gz));
        Location outside = first.getLocation().clone();
        t.tick(10);
        expect(flatTo(first, outside.getX(), outside.getZ()) > 0.5, "but one further out than that goes back to it");
    }

    // ------------------------------------------------------------------ the /dragon commands

    static void commands() {
        Env e = withRoster();
        DragonCommand c = CommandTest.command(e);
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        Kit.TestPlayer other = e.player("Other", 60, 64, 0);
        EnderDragon d = e.adult(o, 4, 64, 0);
        String word = e.svc.wordOf(d);
        EnderDragon second = e.adult(o, 6, 64, 0);
        String word2 = e.svc.wordOf(second);

        CommandTest.run(c, o, "stay");
        expect(o.heard("Usage: /dragon stay"), "stay explains itself: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "stay", "nosuchword");
        expect(o.heard("no dragon with the word"), "an unknown word: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "stay", word);
        expect(e.svc.isStaying(d) && !e.svc.isStaying(second), "stay <word> makes that one wait: " + o.messages);
        expect(o.heard("waits right where it is"), "and says where");
        o.messages.clear();
        CommandTest.run(c, o, "STAY", word2, "HERE");
        expect(e.svc.isStaying(second) && o.heard("comes to where you stand"), "the word 'here' is not case sensitive: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "follow");
        expect(o.heard("Usage: /dragon follow"), "follow explains itself");
        o.messages.clear();
        CommandTest.run(c, o, "follow", word);
        expect(!e.svc.isStaying(d) && o.heard("follows you again"), "follow <word> sends it back: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "follow", word);
        expect(o.heard("already following"), "telling it twice: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "come", "all");
        expect(!e.svc.isStaying(second) && o.heard("1 dragon(s) follow you again"), "come is the same as follow, and all works: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "follow", "all");
        expect(o.heard("None of your dragons was waiting"), "nothing waiting: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "stay", "all");
        expect(e.svc.isStaying(d) && e.svc.isStaying(second) && o.heard("2 dragon(s) will wait where they are"), "stay all: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "stay", "all", "here");
        expect(o.heard("around you here"), "stay all here: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, other, "stay", word);
        expect(other.heard("no dragon with the word"), "someone else's word means nothing: " + other.messages);
        Kit.Console console = new Kit.Console();
        CommandTest.run(c, console, "stay", "all");
        CommandTest.run(c, console, "follow", "all");
        CommandTest.run(c, console, "release", "x");
        CommandTest.run(c, console, "menu");
        expect(console.out.stream().allMatch(m -> m.contains("Run this in game")), "the console is told to run these in game: " + console.out);

        // menu
        o.messages.clear();
        CommandTest.run(c, o, "menu");
        expect(o.heard("not available"), "without a menu wired in it says so: " + o.messages);
        DragonMenu menu = new DragonMenu(e.svc, new ChatPrompt(e.plugin));
        c.setMenu(menu);
        int opens = o.stubOpens;
        CommandTest.run(c, o, "menu");
        expect(o.stubOpens == opens + 1 && o.stubOpened != null && o.stubOpened.getHolder() instanceof DragonMenu.Holder, "the menu opens for the player");

        // release
        o.messages.clear();
        CommandTest.run(c, o, "release");
        expect(o.heard("Usage: /dragon release"), "release explains itself");
        o.messages.clear();
        CommandTest.run(c, o, "release", "zzzzz", "confirm");
        expect(o.heard("no dragon with the word"), "an unknown word");
        o.messages.clear();
        CommandTest.run(c, o, "release", word);
        expect(e.manager.isTame(d) && o.heard("release " + word + " confirm"), "release needs the word 'confirm' and says so: " + o.messages);
        o.messages.clear();
        CommandTest.run(c, o, "release", word, "maybe");
        expect(e.manager.isTame(d), "anything but confirm does nothing");
        CommandTest.run(c, o, "release", word, "CONFIRM");
        expect(!e.manager.isTame(d) && o.heard("wild again"), "confirmed: it is let go: " + o.messages);
        expect(e.svc.ownedCount(o.getUniqueId()) == 1, "and the limit counts one fewer");

        // the list shows the count and the state
        o.messages.clear();
        CommandTest.run(c, o, "list");
        expect(o.heard("Your dragons (1 of 10)") && o.heard("staying"), "the list shows how many and what they are doing: " + o.messages);

        // tab keys
        List<String> subs = c.onTabComplete(o, CommandTest.CMD, "dragon", new String[] {""});
        expect(subs.containsAll(List.of("menu", "stay", "follow", "release")), "the new sub-commands are offered: " + subs);
        List<String> stayWords = c.onTabComplete(o, CommandTest.CMD, "dragon", new String[] {"stay", ""});
        expect(stayWords.contains("all") && stayWords.contains(word2), "stay offers 'all' and the dragon words: " + stayWords);
        expect(c.onTabComplete(o, CommandTest.CMD, "dragon", new String[] {"follow", "a"}).contains("all"), "follow offers all");
        expect(c.onTabComplete(o, CommandTest.CMD, "dragon", new String[] {"release", ""}).contains(word2), "release offers words");
        expect(c.onTabComplete(o, CommandTest.CMD, "dragon", new String[] {"stay", word2, ""}).equals(List.of("here")), "stay <word> offers here");
        expect(c.onTabComplete(o, CommandTest.CMD, "dragon", new String[] {"release", word2, ""}).equals(List.of("confirm")), "release <word> offers confirm");
        expect(c.onTabComplete(o, CommandTest.CMD, "dragon", new String[] {"stay", "zz"}).isEmpty(), "nothing offered for a prefix that matches nothing");
    }
}
