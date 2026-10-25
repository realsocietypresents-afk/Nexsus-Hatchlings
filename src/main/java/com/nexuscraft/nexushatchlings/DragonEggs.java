package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Dragon Eggs that are warming. An egg block that sits on a lit campfire (or soul campfire), or has fire right above it, gains
 * heat; when the heat reaches {@code dragons.egg.hatch-minutes} the egg is gone and a wild baby dragon stands where it was.
 * Heat is kept in {@code eggs.tsv} so a restart does not cool the egg, but time while the server is off does not count.
 */
public final class DragonEggs {

    /** Where an egg is. */
    public record Pos(String world, int x, int y, int z) {
    }

    private static final class Egg {
        long heat;
        long last;
    }

    private final File file;
    private final Consumer<String> warn;
    private final DragonConfig cfg;
    private final DragonService dragons;
    private final LongSupplier clock;
    private final Map<Pos, Egg> eggs = new LinkedHashMap<>();
    private boolean dirty;

    public DragonEggs(File dataFolder, Consumer<String> warn, DragonConfig cfg, DragonService dragons, LongSupplier clock) {
        this.file = new File(dataFolder, "eggs.tsv");
        this.warn = warn;
        this.cfg = cfg;
        this.dragons = dragons;
        this.clock = clock;
    }

    public int size() {
        return eggs.size();
    }

    public boolean has(Pos pos) {
        return eggs.containsKey(pos);
    }

    public static Pos posOf(Block block) {
        return new Pos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    // ------------------------------------------------------------------ heat

    /** True if this egg block is being warmed right now. */
    public static boolean heated(Block egg) {
        Block below = egg.getRelative(0, -1, 0);
        Material under = below.getType();
        if (under == Material.CAMPFIRE || under == Material.SOUL_CAMPFIRE) {
            BlockData data = below.getBlockData();
            if (data instanceof Lightable lightable && lightable.isLit()) {
                return true;
            }
        }
        Material over = egg.getRelative(0, 1, 0).getType();
        return over == Material.FIRE || over == Material.SOUL_FIRE;
    }

    /** Starts keeping track of an egg block (if the block is one). Returns true if it is now tracked. */
    public boolean register(Block block) {
        if (block == null || block.getType() != Material.DRAGON_EGG) {
            return false;
        }
        Pos pos = posOf(block);
        if (!eggs.containsKey(pos)) {
            Egg egg = new Egg();
            egg.last = clock.getAsLong();
            eggs.put(pos, egg);
            dirty = true;
        }
        return true;
    }

    /** How warm the egg is, 0 to 1 (0 if it is not tracked). */
    public double progress(Block block) {
        Egg egg = eggs.get(posOf(block));
        if (egg == null) {
            return 0.0;
        }
        return Math.min(1.0, egg.heat / (double) cfg.hatchMillis());
    }

    /** Minutes of warmth still needed, rounded up. */
    public long minutesLeft(Block block) {
        Egg egg = eggs.get(posOf(block));
        long heat = egg == null ? 0 : egg.heat;
        long left = Math.max(0, cfg.hatchMillis() - heat);
        return (left + 59_999) / 60_000;
    }

    /** One pass over every tracked egg: warm it, drop it if it is gone, hatch it if it is ready. */
    public void tick() {
        long now = clock.getAsLong();
        long cap = cfg.hatchCheckSeconds * 2000L + 1000L;
        for (Map.Entry<Pos, Egg> entry : new ArrayList<>(eggs.entrySet())) {
            Pos pos = entry.getKey();
            Egg egg = entry.getValue();
            World world = Bukkit.getWorld(pos.world());
            if (world == null) {
                egg.last = now;
                continue;
            }
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                // never load a chunk just to warm an egg; the time it sat unloaded does not count
                egg.last = now;
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (block.getType() != Material.DRAGON_EGG) {
                eggs.remove(pos);
                dirty = true;
                continue;
            }
            long elapsed = Math.max(0, Math.min(cap, now - egg.last));
            egg.last = now;
            if (!heated(block)) {
                continue;
            }
            egg.heat += elapsed;
            dirty = true;
            Location centre = block.getLocation().clone().add(0.5, 0.5, 0.5);
            if (egg.heat >= cfg.hatchMillis()) {
                hatch(pos, block);
            } else {
                world.spawnParticle(Particle.FLAME, centre, 4, 0.25, 0.25, 0.25);
                if (egg.heat * 2 >= cfg.hatchMillis()) {
                    world.playSound(centre, Sound.ENTITY_TURTLE_EGG_CRACK, 1.0f, 0.8f);
                }
            }
        }
        if (dirty) {
            flush();
        }
    }

    /** Hatches the egg at once, whatever its heat. Returns true if there was an egg there. */
    public boolean hatch(Pos pos, Block block) {
        if (block.getType() != Material.DRAGON_EGG) {
            return false;
        }
        Location at = block.getLocation().clone().add(0.5, 0.0, 0.5);
        World world = block.getWorld();
        block.setType(Material.AIR);
        eggs.remove(pos);
        dirty = true;
        dragons.spawnBaby(at);
        world.spawnParticle(Particle.DRAGON_BREATH, at.clone().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4);
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.6f);
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (l != null && l.getWorld() != null && l.distance(at) <= 32.0) {
                p.sendMessage(DragonText.PREFIX + "The egg cracks open. A baby dragon crawls out. Throw a Hatchling Crystal at it to tame it.");
            }
        }
        return true;
    }

    /** The tracked egg closest to a point within a distance, or null. */
    public Block nearest(Location from, double within) {
        Block best = null;
        double bestDistance = within;
        for (Pos pos : eggs.keySet()) {
            if (from.getWorld() == null || !pos.world().equals(from.getWorld().getName())) {
                continue;
            }
            double d = Math.sqrt(Math.pow(pos.x() + 0.5 - from.getX(), 2) + Math.pow(pos.y() + 0.5 - from.getY(), 2)
                    + Math.pow(pos.z() + 0.5 - from.getZ(), 2));
            if (d <= bestDistance) {
                bestDistance = d;
                best = from.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
            }
        }
        return best;
    }

    public List<Pos> all() {
        return new ArrayList<>(eggs.keySet());
    }

    // ------------------------------------------------------------------ the file

    public void load() {
        eggs.clear();
        if (!file.isFile()) {
            return;
        }
        long now = clock.getAsLong();
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t");
                try {
                    if (f.length != 5) {
                        throw new NumberFormatException("expected 5 fields");
                    }
                    Egg egg = new Egg();
                    egg.heat = Math.max(0, Long.parseLong(f[4]));
                    egg.last = now;
                    eggs.put(new Pos(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])), egg);
                } catch (NumberFormatException e) {
                    warn.accept("eggs.tsv: skipped a line that does not read (" + line + ")");
                }
            }
        } catch (IOException e) {
            warn.accept("Could not read eggs.tsv: " + e.getMessage());
        }
    }

    public void flush() {
        dirty = false;
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Pos, Egg> e : eggs.entrySet()) {
            Pos p = e.getKey();
            out.append(p.world()).append('\t').append(p.x()).append('\t').append(p.y()).append('\t').append(p.z()).append('\t')
                    .append(e.getValue().heat).append('\n');
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                warn.accept("Could not create " + parent);
                return;
            }
            Files.writeString(file.toPath(), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("Could not write eggs.tsv: " + e.getMessage());
        }
    }
}
