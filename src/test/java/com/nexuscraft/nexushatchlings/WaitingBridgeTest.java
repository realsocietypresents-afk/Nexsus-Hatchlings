package com.nexuscraft.nexushatchlings;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Run twice by hbuild.sh: once with NexusWaiting's classes on the classpath and once without, because
 * the bridge has to behave in both worlds.
 */
public class WaitingBridgeTest {

    static int checks;
    static int failures;

    static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static final class TestPlugin extends JavaPlugin {
    }

    static boolean apiPresent() {
        try {
            Class.forName(WaitingBridge.API_CLASS_NAME);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        if (apiPresent()) {
            PresentCases.run();
        } else {
            absent();
        }
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.exit(1);
        }
    }

    static HatchlingManager manager(WaitingBridge bridge, TestPlugin plugin) {
        HatchlingConfig cfg = new HatchlingConfig(plugin);
        cfg.load();
        HatchlingKeys keys = new HatchlingKeys(plugin);
        TraitRegistry registry = new TraitRegistry(plugin);
        registry.load();
        return new HatchlingManager(plugin, cfg, keys, new TraitManager(registry, keys, cfg), bridge);
    }

    /** Without NexusWaiting installed everything must be a quiet no-op. */
    static void absent() {
        List<String> warns = new ArrayList<>();
        WaitingBridge bridge = new WaitingBridge(warns::add);
        bridge.set(UUID.randomUUID(), "k", "l", 1L);
        bridge.clear(UUID.randomUUID(), "k");
        expect(warns.isEmpty(), "absent: no warning when NexusWaiting is not installed");
        TestPlugin plugin = new TestPlugin();
        HatchlingManager m = manager(bridge, plugin);
        Mob mob = new Mob();
        UUID owner = UUID.randomUUID();
        m.tame(mob, owner);
        expect(m.isTame(mob) && warns.isEmpty(), "absent: taming works as before");
        m.reapplyOne(mob);
        m.release(mob);
        expect(!m.isTame(mob) && warns.isEmpty(), "absent: reapply and release work as before");
        HatchlingManager plain = new HatchlingManager(plugin, new HatchlingConfig(plugin), new HatchlingKeys(plugin),
                new TraitManager(new TraitRegistry(plugin), new HatchlingKeys(plugin), new HatchlingConfig(plugin)));
        expect(plain != null, "absent: the public four-argument constructor still exists");
    }
}
