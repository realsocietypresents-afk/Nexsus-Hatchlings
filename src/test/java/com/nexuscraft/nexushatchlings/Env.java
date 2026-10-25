package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** One server's worth of dragon wiring against the stub server, with a clock the test controls and a flat stone floor under y=64. */
final class Env {

    /** A NexusHouses that says whatever the test tells it to. */
    static final class FakeHouses extends HousesBridge {
        final Set<UUID> pals = new HashSet<>();

        FakeHouses() {
            super(m -> { });
        }

        @Override
        public boolean allied(UUID a, UUID b) {
            return pals.contains(a) && pals.contains(b);
        }
    }

    /** Every "a dragon could not be moved" warning the service logged: a tick that threw is a bug even when nothing else notices. */
    static final List<String> TROUBLE = new ArrayList<>();
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("stub");

    static {
        LOG.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                if (r.getMessage() != null && r.getMessage().contains("could not be moved")) {
                    TROUBLE.add(r.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    final World world = new World("world");
    final Kit.TestPlugin plugin = new Kit.TestPlugin();
    final HatchlingConfig hatch;
    final DragonConfig cfg = new DragonConfig();
    final HatchlingKeys keys;
    final HatchlingManager manager;
    final FakeHouses houses = new FakeHouses();
    final DragonService svc;
    final DragonEggs eggs;
    final AtomicLong clock = new AtomicLong(1_000_000L);
    final List<String> warnings = new ArrayList<>();
    final File dir;
    final Random random = new Random(42);

    Env() {
        Kit.reset();
        Bukkit.WORLDS.clear();
        Bukkit.WORLDS.put("world", world);
        try {
            dir = Files.createTempDirectory("hd-test").toFile();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        hatch = new HatchlingConfig(plugin);
        hatch.load();
        keys = new HatchlingKeys(plugin);
        TraitRegistry registry = new TraitRegistry(plugin);
        registry.load();
        manager = new HatchlingManager(plugin, hatch, keys, new TraitManager(registry, keys, hatch));
        cfg.load(null, warnings::add);
        // Followers spread out in the real plugin; most tests want a dragon to come straight to its owner, so they start with it off.
        cfg.formation = false;
        svc = new DragonService(plugin, hatch, cfg, keys, manager, houses, random);
        manager.setDragons(svc);
        eggs = new DragonEggs(dir, warnings::add, cfg, svc, clock::get);
        floor(60, 63);
    }

    /** A one-block-thick stone floor at level y, from -half to +half in x and z. */
    void floor(int half, int y) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                world.stubSetBlock(x, y, z, Material.STONE);
            }
        }
    }

    void wall(int x, int zFrom, int zTo, int yFrom, int yTo) {
        for (int z = zFrom; z <= zTo; z++) {
            for (int y = yFrom; y <= yTo; y++) {
                world.stubSetBlock(x, y, z, Material.STONE);
            }
        }
    }

    Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    Kit.TestPlayer player(String name, double x, double y, double z) {
        Kit.TestPlayer p = Kit.online(name);
        p.place(world, x, y, z, 0f, 0f);
        return p;
    }

    EnderDragon baby(double x, double y, double z) {
        return svc.spawnBaby(at(x, y, z));
    }

    /** Tames a dragon to a player (as a crystal would) and sets how grown it is, 0 to 1. */
    EnderDragon tame(EnderDragon d, Kit.TestPlayer owner, double grown) {
        manager.tame(d, owner.getUniqueId());
        grow(d, grown);
        return d;
    }

    void grow(Entity d, double fraction) {
        long age = Math.round(fraction * manager.totalDurationMillis());
        d.getPersistentDataContainer().set(keys.tamedAt, HatchlingKeys.LONG, System.currentTimeMillis() - age);
    }

    EnderDragon adult(Kit.TestPlayer owner, double x, double y, double z) {
        return tame(baby(x, y, z), owner, 1.0);
    }

    EnderDragon fledgling(Kit.TestPlayer owner, double x, double y, double z) {
        return tame(baby(x, y, z), owner, 0.6);
    }

    void tick(int n) {
        for (int i = 0; i < n; i++) {
            svc.tick();
        }
    }

    static double dist(Entity a, Entity b) {
        return a.getLocation().distance(b.getLocation());
    }
}
