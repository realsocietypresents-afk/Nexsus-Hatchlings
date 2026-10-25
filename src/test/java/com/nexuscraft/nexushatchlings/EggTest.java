package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.EnderDragon;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexushatchlings.Kit.expect;
import static com.nexuscraft.nexushatchlings.Kit.near;

/** Eggs: what warms them, how they hatch, and what survives a restart. */
public class EggTest {

    public static void main(String[] args) throws Exception {
        heat();
        registering();
        warming();
        hatching();
        vanishing();
        persistence();
        autosave();
        hearing();
        files();
        finding();
        Kit.finish();
    }

    static Block eggOn(Env e, Material under, int x, int z) {
        e.world.getBlockAt(x, 63, z).setType(under);
        Block egg = e.world.getBlockAt(x, 64, z);
        egg.setType(Material.DRAGON_EGG);
        return egg;
    }

    static void heat() {
        Env e = new Env();
        Block egg = eggOn(e, Material.CAMPFIRE, 0, 0);
        expect(DragonEggs.heated(egg), "a lit campfire warms it");
        ((Lightable) egg.getRelative(0, -1, 0).getBlockData()).setLit(false);
        expect(!DragonEggs.heated(egg), "an unlit campfire does not");
        ((Lightable) egg.getRelative(0, -1, 0).getBlockData()).setLit(true);
        expect(DragonEggs.heated(egg), "lit again");
        Block soul = eggOn(e, Material.SOUL_CAMPFIRE, 5, 0);
        expect(DragonEggs.heated(soul), "a soul campfire warms it");
        ((Lightable) soul.getRelative(0, -1, 0).getBlockData()).setLit(false);
        expect(!DragonEggs.heated(soul), "an unlit soul campfire does not");
        Block stone = eggOn(e, Material.STONE, 10, 0);
        expect(!DragonEggs.heated(stone), "stone does not");
        Block furnace = eggOn(e, Material.FURNACE, 12, 0);
        expect(!DragonEggs.heated(furnace), "a furnace is not a campfire");
        Block torch = eggOn(e, Material.STONE, 14, 0);
        e.world.getBlockAt(14, 65, 0).setType(Material.TORCH);
        expect(!DragonEggs.heated(torch), "a torch above does not");
        Block fire = eggOn(e, Material.STONE, 16, 0);
        e.world.getBlockAt(16, 65, 0).setType(Material.FIRE);
        expect(DragonEggs.heated(fire), "fire right above it does");
        Block soulFire = eggOn(e, Material.STONE, 18, 0);
        e.world.getBlockAt(18, 65, 0).setType(Material.SOUL_FIRE);
        expect(DragonEggs.heated(soulFire), "soul fire right above it does");
        Block beside = eggOn(e, Material.STONE, 20, 0);
        e.world.getBlockAt(21, 64, 0).setType(Material.FIRE);
        expect(!DragonEggs.heated(beside), "fire beside it does not");
        Block far = eggOn(e, Material.STONE, 22, 0);
        e.world.getBlockAt(22, 66, 0).setType(Material.FIRE);
        expect(!DragonEggs.heated(far), "fire two blocks above does not");
        Block deep = eggOn(e, Material.STONE, 24, 0);
        e.world.getBlockAt(24, 62, 0).setType(Material.CAMPFIRE);
        expect(!DragonEggs.heated(deep), "a campfire two blocks below does not");
        Block lava = eggOn(e, Material.LAVA, 26, 0);
        expect(!DragonEggs.heated(lava), "lava below does not");
    }

    static void registering() {
        Env e = new Env();
        Block egg = eggOn(e, Material.CAMPFIRE, 0, 0);
        expect(e.eggs.size() == 0, "nothing tracked at first");
        expect(!e.eggs.register(null), "null is not an egg");
        expect(!e.eggs.register(e.world.getBlockAt(3, 64, 3)), "air is not an egg");
        expect(!e.eggs.register(e.world.getBlockAt(0, 63, 0)), "a campfire is not an egg");
        expect(e.eggs.size() == 0, "still nothing");
        expect(e.eggs.register(egg), "an egg is tracked");
        expect(e.eggs.size() == 1 && e.eggs.has(DragonEggs.posOf(egg)), "and counted");
        expect(e.eggs.register(egg) && e.eggs.size() == 1, "tracking twice counts once");
        Block second = eggOn(e, Material.CAMPFIRE, 4, 4);
        e.eggs.register(second);
        expect(e.eggs.size() == 2 && e.eggs.all().size() == 2, "two eggs");
        expect(DragonEggs.posOf(egg).equals(new DragonEggs.Pos("world", 0, 64, 0)), "position of an egg block");
        near(e.eggs.progress(egg), 0.0, 1e-9, "no progress yet");
        near(e.eggs.progress(e.world.getBlockAt(9, 64, 9)), 0.0, 1e-9, "no progress for something not tracked");
        expect(e.eggs.minutesLeft(egg) == 15, "fifteen minutes to go");
        expect(e.eggs.minutesLeft(e.world.getBlockAt(9, 64, 9)) == 15, "an untracked egg would need the full time");
    }

    static void warming() {
        Env e = new Env();
        Block egg = eggOn(e, Material.CAMPFIRE, 0, 0);
        e.eggs.register(egg);
        e.clock.addAndGet(5000);
        e.eggs.tick();
        near(e.eggs.progress(egg), 5000.0 / 900000.0, 1e-9, "five seconds of warmth");
        expect(e.world.stubParticles.contains(Particle.FLAME), "a warming egg shows flames");
        expect(!e.world.stubSounds.contains(Sound.ENTITY_TURTLE_EGG_CRACK), "and does not crack yet");
        expect(e.eggs.minutesLeft(egg) == 15, "a few seconds in, still 15 minutes (rounded up)");
        for (int i = 0; i < 12; i++) {
            e.clock.addAndGet(5000);
            e.eggs.tick();
        }
        near(e.eggs.progress(egg), 65000.0 / 900000.0, 1e-9, "a minute in");
        expect(e.eggs.minutesLeft(egg) == 14, "fourteen minutes left");
        // an unlit fire warms nothing and loses nothing
        ((Lightable) egg.getRelative(0, -1, 0).getBlockData()).setLit(false);
        double before = e.eggs.progress(egg);
        for (int i = 0; i < 20; i++) {
            e.clock.addAndGet(5000);
            e.eggs.tick();
        }
        near(e.eggs.progress(egg), before, 1e-9, "no fire, no progress");
        ((Lightable) egg.getRelative(0, -1, 0).getBlockData()).setLit(true);
        // a long gap (a lag spike, or the server was off) only counts a little
        double was = e.eggs.progress(egg);
        e.clock.addAndGet(3_600_000);
        e.eggs.tick();
        near(e.eggs.progress(egg) - was, 11000.0 / 900000.0, 1e-9, "a long gap counts for at most 2 check intervals and a second");
        // a crack is heard from halfway
        Env h = new Env();
        Block egg2 = eggOn(h, Material.CAMPFIRE, 0, 0);
        h.eggs.register(egg2);
        for (int i = 0; i < 89; i++) {
            h.clock.addAndGet(5000);
            h.eggs.tick();
        }
        expect(!h.world.stubSounds.contains(Sound.ENTITY_TURTLE_EGG_CRACK), "no cracking before halfway (" + h.eggs.progress(egg2) + ")");
        h.clock.addAndGet(5000);
        h.eggs.tick();
        h.clock.addAndGet(5000);
        h.eggs.tick();
        expect(h.world.stubSounds.contains(Sound.ENTITY_TURTLE_EGG_CRACK), "the shell starts to crack past halfway");
    }

    static void hatching() {
        Env e = new Env();
        Block egg = eggOn(e, Material.CAMPFIRE, 0, 0);
        Kit.TestPlayer near = e.player("Near", 10, 64, 0);
        Kit.TestPlayer faraway = e.player("Far", 100, 64, 0);
        e.eggs.register(egg);
        for (int i = 0; i < 179; i++) {
            e.clock.addAndGet(5000);
            e.eggs.tick();
        }
        expect(egg.getType() == Material.DRAGON_EGG, "still an egg just before the time (" + e.eggs.progress(egg) + ")");
        expect(e.svc.loaded().isEmpty(), "no dragon yet");
        e.clock.addAndGet(5000);
        e.eggs.tick();
        expect(e.world.getBlockAt(0, 64, 0).getType() == Material.AIR, "the egg is gone");
        expect(e.world.getBlockAt(0, 63, 0).getType() == Material.CAMPFIRE, "the campfire stays");
        expect(e.eggs.size() == 0, "and is no longer tracked");
        expect(e.svc.loaded().size() == 1, "a dragon stands where the egg was");
        EnderDragon baby = e.svc.loaded().get(0);
        near(baby.getLocation().getX(), 0.5, 1e-9, "in the middle of the block (x)");
        near(baby.getLocation().getZ(), 0.5, 1e-9, "in the middle of the block (z)");
        near(baby.getLocation().getY(), 64, 1e-9, "at its height");
        expect(!e.manager.isTame(baby), "it is wild");
        near(baby.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE).getBaseValue(), 0.12, 1e-9, "and tiny");
        expect(e.world.stubParticles.contains(Particle.DRAGON_BREATH), "with a puff of breath");
        expect(e.world.stubSounds.contains(Sound.ENTITY_ENDER_DRAGON_GROWL), "and a growl");
        expect(near.heard("The egg cracks open") && near.heard("Hatchling Crystal"), "a player nearby is told how to tame it");
        expect(faraway.messages.isEmpty(), "a player far away is not told");
        // hatch is idempotent
        expect(!e.eggs.hatch(DragonEggs.posOf(egg), egg), "nothing left to hatch");
        // a shorter hatch time
        Env quick = new Env();
        quick.cfg.hatchMinutes = 0.1;
        Block q = eggOn(quick, Material.SOUL_CAMPFIRE, 0, 0);
        quick.eggs.register(q);
        quick.clock.addAndGet(5000);
        quick.eggs.tick();
        expect(quick.svc.loaded().isEmpty(), "six seconds is not reached after five");
        quick.clock.addAndGet(5000);
        quick.eggs.tick();
        expect(quick.svc.loaded().size() == 1, "but is after ten");
        // a staff hatch ignores the heat
        Env forced = new Env();
        Block cold = eggOn(forced, Material.STONE, 0, 0);
        forced.eggs.register(cold);
        expect(forced.eggs.hatch(DragonEggs.posOf(cold), cold), "staff can hatch any egg");
        expect(forced.svc.loaded().size() == 1 && forced.eggs.size() == 0, "and it makes a dragon");
        // two eggs hatch separately
        Env two = new Env();
        Block a = eggOn(two, Material.CAMPFIRE, 0, 0);
        Block b = eggOn(two, Material.CAMPFIRE, 8, 0);
        two.eggs.register(a);
        ((Lightable) b.getRelative(0, -1, 0).getBlockData()).setLit(false);
        two.eggs.register(b);
        for (int i = 0; i < 180; i++) {
            two.clock.addAndGet(5000);
            two.eggs.tick();
        }
        expect(two.svc.loaded().size() == 1 && b.getType() == Material.DRAGON_EGG, "only the warm egg hatched");
        expect(two.eggs.size() == 1, "the cold egg is still tracked");
    }

    static void vanishing() {
        Env e = new Env();
        Block egg = eggOn(e, Material.CAMPFIRE, 0, 0);
        e.eggs.register(egg);
        e.clock.addAndGet(5000);
        e.eggs.tick();
        egg.setType(Material.AIR);
        e.clock.addAndGet(5000);
        e.eggs.tick();
        expect(e.eggs.size() == 0, "a broken egg is forgotten");
        expect(e.svc.loaded().isEmpty(), "and does not hatch");
        // an egg in a world that is not loaded is kept, and does not warm
        Env w = new Env();
        World nether = new World("nether");
        Bukkit.WORLDS.put("nether", nether);
        nether.getBlockAt(0, 63, 0).setType(Material.CAMPFIRE);
        Block there = nether.getBlockAt(0, 64, 0);
        there.setType(Material.DRAGON_EGG);
        w.eggs.register(there);
        w.clock.addAndGet(5000);
        w.eggs.tick();
        near(w.eggs.progress(there), 5000.0 / 900000.0, 1e-9, "eggs in other worlds warm too");
        Bukkit.WORLDS.remove("nether");
        w.clock.addAndGet(5000);
        w.eggs.tick();
        expect(w.eggs.size() == 1, "an egg whose world is not loaded stays on the list");
        Bukkit.WORLDS.put("nether", nether);
        w.clock.addAndGet(5000);
        w.eggs.tick();
        near(w.eggs.progress(there), 10000.0 / 900000.0, 1e-9, "and the time it was unloaded did not count");
        // an egg whose chunk is not loaded is left alone: nothing counts, and the block is never asked for
        final int[] asked = {0};
        World far = new World("far") {
            @Override
            public Block getBlockAt(int x, int y, int z) {
                asked[0]++;
                return super.getBlockAt(x, y, z);
            }
        };
        Bukkit.WORLDS.put("far", far);
        far.getBlockAt(160, 63, 160).setType(Material.CAMPFIRE);
        Block distant = far.getBlockAt(160, 64, 160);
        distant.setType(Material.DRAGON_EGG);
        w.eggs.register(distant);
        far.chunkLoaded = (cx, cz) -> !(cx == 10 && cz == 10);
        asked[0] = 0;
        w.clock.addAndGet(5000);
        w.eggs.tick();
        expect(asked[0] == 0, "an unloaded chunk is not touched: " + asked[0]);
        near(w.eggs.progress(distant), 0.0, 1e-9, "and the egg did not warm");
        expect(w.eggs.size() == 2, "it stays on the list");
        far.chunkLoaded = null;
        w.clock.addAndGet(5000);
        w.eggs.tick();
        near(w.eggs.progress(distant), 5000.0 / 900000.0, 1e-9, "once loaded it warms from then on, the gap not counted");
        Bukkit.WORLDS.remove("far");
        Bukkit.WORLDS.remove("nether");
    }

    /** Whatever changes the list is on disk by the end of the next tick, with no explicit flush. */
    static void autosave() throws Exception {
        Env e = new Env();
        File file = new File(e.dir, "eggs.tsv");
        Block cold = eggOn(e, Material.STONE, 0, 0);
        e.eggs.register(cold);
        e.clock.addAndGet(5000);
        e.eggs.tick();
        expect(file.isFile() && Files.readAllLines(file.toPath()).equals(List.of("world\t0\t64\t0\t0")),
                "a newly placed cold egg is saved by the next tick: " + (file.isFile() ? Files.readAllLines(file.toPath()) : "no file"));
        cold.setType(Material.AIR);
        e.clock.addAndGet(5000);
        e.eggs.tick();
        expect(Files.readAllLines(file.toPath()).isEmpty() || Files.readString(file.toPath()).isEmpty(), "a broken egg is dropped from the file by the next tick");
        Block warm = eggOn(e, Material.CAMPFIRE, 4, 4);
        e.eggs.register(warm);
        e.eggs.flush();
        e.eggs.hatch(DragonEggs.posOf(warm), warm);
        e.clock.addAndGet(5000);
        e.eggs.tick();
        expect(Files.readString(file.toPath()).isEmpty(), "a hatched egg is dropped from the file by the next tick");
    }

    static void hearing() {
        Env e = new Env();
        Block egg = eggOn(e, Material.CAMPFIRE, 4, 4);
        Kit.TestPlayer on = e.player("On", 36.5, 64, 4.5);
        Kit.TestPlayer past = e.player("Past", 36.6, 64, 4.5);
        e.eggs.register(egg);
        e.eggs.hatch(DragonEggs.posOf(egg), egg);
        expect(on.heard("The egg cracks open"), "someone exactly 32 blocks away is told: " + on.messages);
        expect(!past.heard("The egg cracks open"), "someone a little further is not: " + past.messages);
    }

    static void persistence() throws Exception {
        Env e = new Env();
        Block egg = eggOn(e, Material.CAMPFIRE, 0, 0);
        Block other = eggOn(e, Material.CAMPFIRE, 6, -3);
        e.eggs.register(egg);
        e.eggs.register(other);
        for (int i = 0; i < 6; i++) {
            e.clock.addAndGet(5000);
            e.eggs.tick();
        }
        File file = new File(e.dir, "eggs.tsv");
        expect(file.isFile(), "heat is saved as it grows");
        List<String> lines = Files.readAllLines(file.toPath());
        expect(lines.size() == 2, "one line per egg");
        expect(lines.contains("world\t0\t64\t0\t30000") && lines.contains("world\t6\t64\t-3\t30000"), "with its place and heat: " + lines);
        e.clock.addAndGet(86_400_000L);
        DragonEggs again = new DragonEggs(e.dir, e.warnings::add, e.cfg, e.svc, e.clock::get);
        again.load();
        expect(again.size() == 2, "both come back after a restart");
        near(again.progress(egg), 30000.0 / 900000.0, 1e-9, "with their heat");
        again.tick();
        near(again.progress(egg), 30000.0 / 900000.0, 1e-9, "the time the server was off does not count");
        e.clock.addAndGet(5000);
        again.tick();
        near(again.progress(egg), 35000.0 / 900000.0, 1e-9, "warming carries on after that");
        // a hatched egg leaves the file
        e.eggs.hatch(DragonEggs.posOf(other), other);
        e.eggs.flush();
        expect(Files.readAllLines(file.toPath()).size() == 1, "a hatched egg is removed from the file");
        // flush with nothing leaves an empty file
        e.eggs.hatch(DragonEggs.posOf(egg), egg);
        e.eggs.flush();
        expect(Files.readString(file.toPath()).isEmpty(), "no eggs, empty file");
        // loading again replaces what was held
        e.eggs.register(eggOn(e, Material.CAMPFIRE, 9, 9));
        e.eggs.load();
        expect(e.eggs.size() == 0, "load replaces the list with what the file says");
        // the data folder is made if missing
        File nested = new File(e.dir, "deeper/still");
        DragonEggs fresh = new DragonEggs(nested, e.warnings::add, e.cfg, e.svc, e.clock::get);
        fresh.register(eggOn(e, Material.CAMPFIRE, 12, 12));
        fresh.flush();
        expect(new File(nested, "eggs.tsv").isFile(), "a missing data folder is created");
    }

    static void files() throws Exception {
        Env e = new Env();
        File file = new File(e.dir, "eggs.tsv");
        expect(!file.exists(), "no file yet");
        e.eggs.load();
        expect(e.eggs.size() == 0 && e.warnings.isEmpty(), "no file is fine");
        Files.writeString(file.toPath(), "world\t1\t64\t1\t1000\n\nnot a line\nworld\tx\t64\t1\t5\nworld\t2\t64\t2\t-50\nworld\t3\t64\t3\t7\textra\nworld\t4\t64\t4\t99\n");
        e.eggs.load();
        expect(e.eggs.size() == 3, "good lines load, bad ones are skipped (" + e.eggs.size() + ")");
        expect(e.warnings.size() == 3, "and each bad one is reported: " + e.warnings);
        expect(e.warnings.get(0).contains("not a line"), "the warning shows the line");
        e.eggs.register(e.world.getBlockAt(2, 64, 2));
        near(e.eggs.progress(eggOn(e, Material.STONE, 2, 2)), 0.0, 1e-9, "a negative heat is read as zero");
        // a directory where the file should be cannot be read
        Env d = new Env();
        new File(d.dir, "eggs.tsv").mkdirs();
        d.eggs.load();
        expect(d.eggs.size() == 0 && d.warnings.isEmpty(), "a folder where the file should be is treated as no file");
        d.eggs.register(eggOn(d, Material.CAMPFIRE, 0, 0));
        d.eggs.flush();
        expect(d.warnings.size() == 1 && d.warnings.get(0).contains("Could not write eggs.tsv"), "but writing to it is reported: " + d.warnings);
        // a file that cannot be decoded is reported and ignored
        Env bad = new Env();
        Files.write(new File(bad.dir, "eggs.tsv").toPath(), new byte[]{(byte) 0xff, (byte) 0xfe, (byte) 0xfa, '\n'});
        bad.eggs.load();
        expect(bad.eggs.size() == 0 && bad.warnings.size() == 1 && bad.warnings.get(0).contains("Could not read eggs.tsv"),
                "an unreadable file is reported once: " + bad.warnings);
        // a data folder that cannot be created
        Env f = new Env();
        File blocker = new File(f.dir, "blocker");
        Files.writeString(blocker.toPath(), "x");
        DragonEggs stuck = new DragonEggs(new File(blocker, "inside"), f.warnings::add, f.cfg, f.svc, f.clock::get);
        stuck.register(eggOn(f, Material.CAMPFIRE, 0, 0));
        stuck.flush();
        expect(f.warnings.size() == 1 && f.warnings.get(0).contains("Could not create"), "a folder that cannot be made is reported: " + f.warnings);
    }

    static void finding() {
        Env e = new Env();
        Block a = eggOn(e, Material.CAMPFIRE, 0, 0);
        Block b = eggOn(e, Material.CAMPFIRE, 6, 0);
        e.eggs.register(a);
        e.eggs.register(b);
        expect(e.eggs.nearest(e.at(1, 64, 0), 10) != null && e.eggs.nearest(e.at(1, 64, 0), 10).getX() == 0, "the nearest egg");
        expect(e.eggs.nearest(e.at(5, 64, 0), 10).getX() == 6, "the nearer of two");
        expect(e.eggs.nearest(e.at(50, 64, 0), 10) == null, "none in range");
        expect(e.eggs.nearest(new Location(new World("nether"), 0, 64, 0), 100) == null, "none in another world");
        expect(e.eggs.nearest(new Location(null, 0, 64, 0), 100) == null, "none with no world");
        expect(e.eggs.nearest(e.at(0.5, 64.5, 0.5), 0.0) != null, "at the centre of an egg at range 0");
        expect(e.eggs.nearest(e.at(0.5, 64.5, 3.5), 2.9) == null && e.eggs.nearest(e.at(0.5, 64.5, 3.5), 3.1) != null, "range is measured to the centre of the block");
    }
}
