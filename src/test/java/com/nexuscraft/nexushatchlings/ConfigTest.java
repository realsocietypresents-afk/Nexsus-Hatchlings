package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexushatchlings.Kit.expect;
import static com.nexuscraft.nexushatchlings.Kit.near;

/** dragons: in config.yml, and the soft bridge to NexusHouses. */
public class ConfigTest {

    public static void main(String[] args) throws Exception {
        defaults();
        values();
        clamping();
        stages();
        shipped();
        traitList();
        houses();
        Kit.finish();
    }

    /** Remembers which paths were asked for. */
    static final class Recording extends Kit.MapSection {
        final Set<String> asked = new LinkedHashSet<>();

        @Override public boolean getBoolean(String path, boolean def) { asked.add(path); return super.getBoolean(path, def); }
        @Override public double getDouble(String path, double def) { asked.add(path); return super.getDouble(path, def); }
    }

    static void defaults() {
        List<String> warnings = new ArrayList<>();
        DragonConfig d = new DragonConfig();
        d.load(null, warnings::add);
        expect(warnings.isEmpty(), "no section: no warnings");
        expect(d.enabled && d.rideEnabled && d.attackEnabled && d.glow && d.aura && d.wander && d.useHouses && d.rideOwnerOnly, "switches default on");
        near(d.hatchMinutes, 15, 1e-9, "hatch minutes");
        expect(d.hatchCheckSeconds == 5, "check seconds");
        near(d.flyAt, 0.5, 1e-9, "fly at half");
        near(d.adultAt, 1.0, 1e-9, "adult at the end");
        expect(d.moveIntervalTicks == 2, "move interval");
        near(d.walkSpeed, 3, 1e-9, "walk speed");
        near(d.followFlySpeed, 10, 1e-9, "follow fly speed");
        near(d.descendSpeed, 6, 1e-9, "descend speed");
        near(d.wanderRadius, 6, 1e-9, "wander radius");
        near(d.yawOffset, 180, 1e-9, "yaw offset");
        near(d.rideSpeed, 14, 1e-9, "ride speed");
        expect(d.rideClearance == 3 && d.slowFallSeconds == 6, "ride clearance and slow fall");
        expect(d.auraIntervalTicks == 10, "aura interval");
        near(d.commandRange, 200, 1e-9, "command range");
        near(d.targetRange, 64, 1e-9, "target range");
        near(d.strikeRange, 22, 1e-9, "strike range");
        near(d.attackSpeed, 14, 1e-9, "attack speed");
        near(d.hoverHeight, 6, 1e-9, "hover height");
        near(d.damage, 8, 1e-9, "damage");
        expect(d.intervalTicks == 30 && d.fireSeconds == 5 && d.durationSeconds == 45, "attack timing");
        expect(d.hatchMillis() == 900_000L, "15 minutes is 900000 ms");
        DragonConfig e = new DragonConfig();
        e.load(new Kit.MapSection(), warnings::add);
        expect(warnings.isEmpty(), "empty section: no warnings");
        expect(same(d, e), "an empty section gives the defaults");
    }

    static boolean same(DragonConfig a, DragonConfig b) {
        try {
            for (Field f : DragonConfig.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!f.get(a).equals(f.get(b))) {
                    System.out.println("  differs: " + f.getName() + " " + f.get(a) + " vs " + f.get(b));
                    return false;
                }
            }
            return true;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void values() {
        Kit.MapSection s = new Kit.MapSection()
                .put("dragons.enabled", false)
                .put("dragons.egg.hatch-minutes", 2.5)
                .put("dragons.egg.check-seconds", 10)
                .put("dragons.stages.fly-at-percent", 25)
                .put("dragons.stages.adult-at-percent", 75)
                .put("dragons.movement.interval-ticks", 4)
                .put("dragons.movement.walk-speed", 2.5)
                .put("dragons.movement.follow-fly-speed", 12.5)
                .put("dragons.movement.descend-speed", 8)
                .put("dragons.movement.wild-wander", false)
                .put("dragons.movement.wander-radius", 9)
                .put("dragons.movement.yaw-offset", 0)
                .put("dragons.ride.enabled", false)
                .put("dragons.ride.owner-only", false)
                .put("dragons.ride.speed", 20)
                .put("dragons.ride.clearance-blocks", 5)
                .put("dragons.ride.slow-fall-seconds", 10)
                .put("dragons.look.glow", false)
                .put("dragons.look.aura", false)
                .put("dragons.look.aura-interval-ticks", 20)
                .put("dragons.attack.enabled", false)
                .put("dragons.attack.command-range", 300)
                .put("dragons.attack.target-range", 100)
                .put("dragons.attack.strike-range", 30)
                .put("dragons.attack.speed", 20)
                .put("dragons.attack.hover-height", 10)
                .put("dragons.attack.damage", 12.5)
                .put("dragons.attack.interval-ticks", 60)
                .put("dragons.attack.fire-seconds", 8)
                .put("dragons.attack.duration-seconds", 90)
                .put("dragons.allies.use-nexushouses", false);
        List<String> warnings = new ArrayList<>();
        DragonConfig d = new DragonConfig();
        d.load(s, warnings::add);
        expect(warnings.isEmpty(), "valid values give no warnings: " + warnings);
        expect(!d.enabled, "enabled read");
        near(d.hatchMinutes, 2.5, 1e-9, "hatch minutes read");
        expect(d.hatchMillis() == 150_000L, "2.5 minutes is 150000 ms");
        expect(d.hatchCheckSeconds == 10, "check seconds read");
        near(d.flyAt, 0.25, 1e-9, "fly percent read");
        near(d.adultAt, 0.75, 1e-9, "adult percent read");
        expect(d.moveIntervalTicks == 4, "interval read");
        near(d.walkSpeed, 2.5, 1e-9, "walk read");
        near(d.followFlySpeed, 12.5, 1e-9, "follow fly read");
        near(d.descendSpeed, 8, 1e-9, "descend read");
        expect(!d.wander, "wander read");
        near(d.wanderRadius, 9, 1e-9, "radius read");
        near(d.yawOffset, 0, 1e-9, "yaw offset read");
        expect(!d.rideEnabled && !d.rideOwnerOnly, "ride switches read");
        near(d.rideSpeed, 20, 1e-9, "ride speed read");
        expect(d.rideClearance == 5 && d.slowFallSeconds == 10, "ride numbers read");
        expect(!d.glow && !d.aura && d.auraIntervalTicks == 20, "look read");
        expect(!d.attackEnabled, "attack enabled read");
        near(d.commandRange, 300, 1e-9, "command range read");
        near(d.targetRange, 100, 1e-9, "target range read");
        near(d.strikeRange, 30, 1e-9, "strike range read");
        near(d.attackSpeed, 20, 1e-9, "attack speed read");
        near(d.hoverHeight, 10, 1e-9, "hover read");
        near(d.damage, 12.5, 1e-9, "damage read");
        expect(d.intervalTicks == 60 && d.fireSeconds == 8 && d.durationSeconds == 90, "attack timing read");
        expect(!d.useHouses, "houses read");
        // a second load replaces the first
        d.load(null, warnings::add);
        expect(d.enabled && d.hatchMinutes == 15.0, "loading again goes back to defaults for anything missing");
    }

    /** A section to start from: the adult threshold is checked against the fly threshold, so move that out of the way when testing it. */
    static Kit.MapSection base(String key) {
        Kit.MapSection s = new Kit.MapSection();
        if (key.equals("dragons.stages.adult-at-percent")) {
            s.put("dragons.stages.fly-at-percent", 0);
        }
        return s;
    }

    static void clamping() {
        // key, too low, too high, lowest allowed, highest allowed
        Object[][] rows = {
                {"dragons.egg.hatch-minutes", 0.0, 5000.0, 0.1, 1440.0},
                {"dragons.egg.check-seconds", 0, 500, 1.0, 120.0},
                {"dragons.stages.fly-at-percent", -5, 150, 0.0, 100.0},
                {"dragons.stages.adult-at-percent", -5, 150, 0.0, 100.0},
                {"dragons.movement.interval-ticks", 0, 50, 1.0, 20.0},
                {"dragons.movement.walk-speed", 0.1, 100, 0.5, 20.0},
                {"dragons.movement.follow-fly-speed", 0.1, 100, 1.0, 40.0},
                {"dragons.movement.descend-speed", 0.1, 100, 1.0, 40.0},
                {"dragons.movement.wander-radius", 0.1, 100, 1.0, 32.0},
                {"dragons.movement.yaw-offset", -500, 500, -360.0, 360.0},
                {"dragons.ride.speed", 0.1, 100, 1.0, 40.0},
                {"dragons.ride.clearance-blocks", 0, 50, 1.0, 12.0},
                {"dragons.ride.slow-fall-seconds", -1, 500, 0.0, 60.0},
                {"dragons.look.aura-interval-ticks", 1, 500, 2.0, 100.0},
                {"dragons.attack.command-range", 1, 5000, 10.0, 1000.0},
                {"dragons.attack.target-range", 1, 5000, 8.0, 256.0},
                {"dragons.attack.strike-range", 1, 500, 4.0, 64.0},
                {"dragons.attack.speed", 0.1, 100, 1.0, 40.0},
                {"dragons.attack.hover-height", -1, 100, 0.0, 30.0},
                {"dragons.attack.damage", 0.1, 500, 0.5, 100.0},
                {"dragons.attack.interval-ticks", 1, 5000, 5.0, 400.0},
                {"dragons.attack.fire-seconds", -1, 500, 0.0, 60.0},
                {"dragons.attack.duration-seconds", 1, 5000, 5.0, 600.0},
        };
        for (Object[] row : rows) {
            String key = (String) row[0];
            for (int side = 0; side < 2; side++) {
                Object bad = row[1 + side];
                double wantedAt = side == 0 ? (double) row[3] : (double) row[4];
                List<String> warnings = new ArrayList<>();
                DragonConfig d = new DragonConfig();
                d.load(base(key).put(key, bad), warnings::add);
                expect(warnings.size() == 1 && warnings.get(0).startsWith(key + " must be between"), (side == 0 ? "low " : "high ") + key + " warns once: " + warnings);
                DragonConfig clean = new DragonConfig();
                clean.load(base(key).put(key, wantedAt), new ArrayList<String>()::add);
                DragonConfig clamped = new DragonConfig();
                clamped.load(base(key).put(key, bad), new ArrayList<String>()::add);
                expect(same(clean, clamped), (side == 0 ? "low " : "high ") + key + " is moved to the limit " + wantedAt);
                // and the limit itself is accepted silently
                List<String> quiet = new ArrayList<>();
                new DragonConfig().load(base(key).put(key, wantedAt), quiet::add);
                expect(quiet.isEmpty(), "the limit " + wantedAt + " of " + key + " is allowed: " + quiet);
            }
        }
        // NaN falls back to the default and says so
        List<String> warnings = new ArrayList<>();
        DragonConfig d = new DragonConfig();
        d.load(new Kit.MapSection().put("dragons.attack.damage", Double.NaN), warnings::add);
        near(d.damage, 8.0, 1e-9, "NaN gives the default");
        expect(warnings.size() == 1 && warnings.get(0).contains("using 8"), "and says so: " + warnings);
        // the message for a fraction shows the fraction
        warnings.clear();
        new DragonConfig().load(new Kit.MapSection().put("dragons.egg.hatch-minutes", 0.01), warnings::add);
        expect(warnings.get(0).contains("0.1") && warnings.get(0).contains("1440"), "fractions are shown plainly: " + warnings);
        // text where a number belongs is just the default (the stub treats it as missing)
        warnings.clear();
        DragonConfig t = new DragonConfig();
        t.load(new Kit.MapSection().put("dragons.attack.damage", "lots"), warnings::add);
        near(t.damage, 8.0, 1e-9, "text instead of a number gives the default");
    }

    static void stages() {
        List<String> warnings = new ArrayList<>();
        DragonConfig d = new DragonConfig();
        d.load(new Kit.MapSection().put("dragons.stages.fly-at-percent", 80).put("dragons.stages.adult-at-percent", 40), warnings::add);
        near(d.flyAt, 0.8, 1e-9, "fly kept");
        near(d.adultAt, 0.8, 1e-9, "adult raised to match fly when it was lower");
        expect(warnings.size() == 1 && warnings.get(0).contains("lower than fly-at-percent") && warnings.get(0).contains("80"), "and it says so: " + warnings);
        warnings.clear();
        DragonConfig same = new DragonConfig();
        same.load(new Kit.MapSection().put("dragons.stages.fly-at-percent", 60).put("dragons.stages.adult-at-percent", 60), warnings::add);
        expect(warnings.isEmpty() && same.flyAt == same.adultAt, "equal thresholds are fine");
        warnings.clear();
        DragonConfig zero = new DragonConfig();
        zero.load(new Kit.MapSection().put("dragons.stages.fly-at-percent", 0).put("dragons.stages.adult-at-percent", 0), warnings::add);
        expect(warnings.isEmpty() && zero.flyAt == 0.0, "zero is allowed: adult from the start");
        expect(DragonStage.of(0.0, zero.flyAt, zero.adultAt) == DragonStage.ADULT, "and means adult at birth");
    }

    static void shipped() throws Exception {
        String path = System.getProperty("shipped.config");
        expect(path != null, "the flattened config.yml was handed to the test");
        if (path == null) {
            return;
        }
        Recording flat = new Recording();
        for (String line : Files.readAllLines(Path.of(path))) {
            String[] f = line.split("\t", 3);
            if (f.length < 3 || !f[0].startsWith("dragons.")) {
                continue;
            }
            switch (f[1]) {
                case "b" -> flat.put(f[0], Boolean.parseBoolean(f[2]));
                case "i" -> flat.put(f[0], Integer.parseInt(f[2]));
                case "d" -> flat.put(f[0], Double.parseDouble(f[2]));
                default -> flat.put(f[0], f[2]);
            }
        }
        expect(flat.values.size() >= 30, "the shipped file has the dragons section (" + flat.values.size() + " keys)");
        List<String> warnings = new ArrayList<>();
        DragonConfig loaded = new DragonConfig();
        loaded.load(flat, warnings::add);
        expect(warnings.isEmpty(), "shipped config: no warnings: " + warnings);
        expect(same(loaded, new DragonConfig()), "shipped config equals the built-in defaults");
        Set<String> shipped = new HashSet<>(flat.values.keySet());
        Set<String> read = new HashSet<>(flat.asked);
        Set<String> unused = new HashSet<>(shipped);
        unused.removeAll(read);
        Set<String> missing = new HashSet<>(read);
        missing.removeAll(shipped);
        expect(unused.isEmpty(), "every shipped dragons key is read by the code: " + unused);
        expect(missing.isEmpty(), "every key the code reads is in the shipped file: " + missing);
    }

    static java.util.Map<String, Object> trait(String id, String kind, Object... more) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("display-name", id.toUpperCase());
        m.put("kind", kind);
        for (int i = 0; i + 1 < more.length; i += 2) {
            m.put((String) more[i], more[i + 1]);
        }
        return m;
    }

    /** The trait list lives at traits.definitions (traits: itself holds numbers, so it cannot also be the list). */
    static void traitList() {
        Kit.TestPlugin plugin = new Kit.TestPlugin();
        plugin.getConfig().set("traits.definitions", List.of(
                trait("a", "STAT", "attribute", "GENERIC_MAX_HEALTH", "multiplier", 1.5),
                trait("b", "ABILITY", "ability", "SWIFT"),
                trait("c", "COSMETIC", "prefix", "x")));
        TraitRegistry registry = new TraitRegistry(plugin);
        registry.load();
        expect(registry.all().size() == 3, "traits are read from traits.definitions (" + registry.all().size() + ")");
        Kit.TestPlugin old = new Kit.TestPlugin();
        old.getConfig().set("traits", List.of(trait("a", "ABILITY", "ability", "SWIFT")));
        TraitRegistry oldStyle = new TraitRegistry(old);
        oldStyle.load();
        expect(oldStyle.all().isEmpty(), "a list placed directly under traits: is not read (it is not valid next to the numbers)");
        Kit.TestPlugin none = new Kit.TestPlugin();
        TraitRegistry empty = new TraitRegistry(none);
        empty.load();
        expect(empty.all().isEmpty() && empty.enabled() && empty.countMin() == 1 && empty.countMax() == 2, "no list: no traits, default counts");
    }

    // ------------------------------------------------------------------ the NexusHouses bridge

    /** A pretend NexusHouses API with the two methods the bridge calls. */
    public static class FakeApi {
        public final java.util.Map<UUID, UUID> houseOf = new java.util.HashMap<>();
        public final java.util.Map<UUID, Object> blockOf = new java.util.HashMap<>();
        public boolean explode;

        public UUID houseIdOf(UUID player) {
            if (explode) {
                throw new IllegalStateException("houses are on fire");
            }
            return houseOf.get(player);
        }

        public Object allianceBlockOf(UUID house) {
            return blockOf.get(house);
        }
    }

    /** A pretend API that is missing a method. */
    public static class BrokenApi {
        public UUID houseIdOf(UUID player) {
            return null;
        }
    }

    static final String FAKE = "com.nexuscraft.nexushatchlings.ConfigTest$FakeApi";
    static final String BROKEN = "com.nexuscraft.nexushatchlings.ConfigTest$BrokenApi";
    static final Kit.TestPlugin OWNER = new Kit.TestPlugin();

    static void houses() {
        Bukkit.getServicesManager().unregisterAll(OWNER);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID red = UUID.randomUUID();
        UUID blue = UUID.randomUUID();
        UUID green = UUID.randomUUID();
        List<String> warnings = new ArrayList<>();

        HousesBridge missingClass = new HousesBridge(warnings::add, "no.such.Class");
        expect(!missingClass.allied(a, b), "NexusHouses not installed: nobody is allied");
        expect(!missingClass.allied(a, b), "and it stays that way");
        expect(warnings.isEmpty(), "and it never complains");

        HousesBridge b1 = new HousesBridge(warnings::add, FAKE);
        expect(!b1.allied(a, b), "installed but not registered yet: not allied");
        FakeApi api = new FakeApi();
        Bukkit.getServicesManager().register(FakeApi.class, api, OWNER, ServicePriority.Normal);
        expect(!b1.allied(a, b), "registered, but players are in no house");
        api.houseOf.put(a, red);
        expect(!b1.allied(a, b), "one of them in no house");
        expect(!b1.allied(b, a), "either way round");
        api.houseOf.put(b, red);
        expect(b1.allied(a, b), "the same house is allied");
        api.houseOf.put(c, blue);
        expect(!b1.allied(a, c), "different houses, no alliance");
        api.blockOf.put(red, Set.of(green));
        expect(!b1.allied(a, c), "an alliance block that does not list the other house");
        api.blockOf.put(red, Set.of(blue));
        expect(b1.allied(a, c), "an alliance block that lists the other house");
        expect(!b1.allied(c, a), "alliances are read from the first player's side");
        api.blockOf.put(red, "not a set");
        expect(!b1.allied(a, c), "an alliance block of the wrong kind counts for nothing");
        api.blockOf.put(red, null);
        expect(!b1.allied(a, c), "no alliance block at all");
        expect(!b1.allied(null, b), "null first player");
        expect(!b1.allied(a, null), "null second player");
        expect(!b1.allied(null, null), "both null");
        expect(warnings.isEmpty(), "none of that was worth a warning");
        api.explode = true;
        expect(!b1.allied(a, b), "a failing NexusHouses means not allied");
        expect(!b1.allied(a, c), "still not allied");
        expect(warnings.size() == 1 && warnings.get(0).contains("did not answer the way this version expects"), "and the first failure is reported once: " + warnings);
        api.explode = false;
        expect(b1.allied(a, b), "and it recovers when NexusHouses does");
        HousesBridge fresh = new HousesBridge(warnings::add, FAKE);
        expect(fresh.allied(a, b), "a bridge whose very first question is about two housemates already says yes");

        Bukkit.getServicesManager().unregisterAll(OWNER);
        warnings.clear();
        HousesBridge b2 = new HousesBridge(warnings::add, BROKEN);
        Bukkit.getServicesManager().register(BrokenApi.class, new BrokenApi(), OWNER, ServicePriority.Normal);
        expect(!b2.allied(a, b), "an API missing a method means not allied");
        expect(!b2.allied(a, b), "and again");
        expect(warnings.size() == 1, "reported once: " + warnings);
        Bukkit.getServicesManager().unregisterAll(OWNER);

        HousesBridge real = new HousesBridge(warnings::add);
        expect(!real.allied(a, b), "the public constructor looks for the real NexusHouses class and, finding none, says no");
    }
}
