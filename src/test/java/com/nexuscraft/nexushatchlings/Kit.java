package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared test doubles: players that remember what they were told, a map-backed config. */
final class Kit {

    static int checks;
    static int failures;

    static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static void near(double actual, double wanted, double tolerance, String what) {
        expect(Math.abs(actual - wanted) <= tolerance, what + " (got " + actual + ", wanted " + wanted + ")");
    }

    static void finish() {
        expect(Env.TROUBLE.isEmpty(), "no dragon tick threw: " + Env.TROUBLE);
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.exit(1);
        }
    }

    static final class TestPlugin extends JavaPlugin {
    }

    static void reset() {
        Bukkit.PLAYERS.clear();
        Bukkit.ENTITIES.clear();
        Bukkit.DISPATCHED.clear();
        Bukkit.SELECTED.clear();
        org.bukkit.plugin.PluginManager.ENABLED.clear();
        org.bukkit.plugin.PluginManager.PLUGINS.clear();
        org.bukkit.scheduler.BukkitScheduler.LATER.clear();
        org.bukkit.scheduler.BukkitScheduler.REPEATING.clear();
        org.bukkit.scheduler.BukkitScheduler.HANDLES.clear();
        org.bukkit.scheduler.BukkitScheduler.DELAYS.clear();
        org.bukkit.scheduler.BukkitScheduler.PERIODS.clear();
    }

    static final class TestPlayer extends Player {
        final String name;
        final List<String> messages = new ArrayList<>();
        final Set<String> perms = new HashSet<>();
        final List<PotionEffect> effects = new ArrayList<>();
        boolean online = true;
        boolean dead;
        /** When true, damage is ignored (a protection plugin cancelled the event). */
        boolean shielded;

        TestPlayer(String name) {
            this.name = name;
        }

        /** Put the player somewhere, looking some way. */
        TestPlayer place(org.bukkit.World world, double x, double y, double z, float yaw, float pitch) {
            stubSetLocation(new org.bukkit.Location(world, x, y, z, yaw, pitch));
            return this;
        }

        TestPlayer look(float yaw, float pitch) {
            org.bukkit.Location l = getLocation().clone();
            l.setYaw(yaw);
            l.setPitch(pitch);
            stubSetLocation(l);
            return this;
        }

        /** Like a real server: when a vehicle carries a rider somewhere, the rider keeps looking the way they were looking. */
        @Override
        public boolean teleport(org.bukkit.Location location) {
            org.bukkit.Location was = getLocation();
            org.bukkit.Location moved = location.clone();
            moved.setYaw(was.getYaw());
            moved.setPitch(was.getPitch());
            return super.teleport(moved);
        }

        @Override
        public void damage(double amount, org.bukkit.entity.Entity source) {
            if (!shielded) {
                super.damage(amount, source);
            }
        }

        @Override public String getName() { return name; }
        @Override public boolean isOnline() { return online; }
        @Override public boolean isDead() { return dead; }
        @Override public boolean hasPermission(String node) { return perms.contains(node); }
        @Override public void sendMessage(String m) { messages.add(m); }
        @Override public boolean addPotionEffect(PotionEffect effect) { effects.add(effect); return true; }

        boolean heard(String fragment) {
            for (String m : messages) {
                if (m.replaceAll("§.", "").contains(fragment)) {
                    return true;
                }
            }
            return false;
        }

        String last() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1).replaceAll("§.", "");
        }
    }

    /** A command sender that is not a player. */
    static final class Console implements org.bukkit.command.CommandSender {
        final List<String> out = new ArrayList<>();
        boolean allowed = true;

        @Override public boolean hasPermission(String node) { return allowed; }
        @Override public void sendMessage(String message) { out.add(message); }
        @Override public void sendMessage(net.kyori.adventure.text.Component message) { }
        @Override public String getName() { return "CONSOLE"; }

        boolean said(String fragment) {
            for (String m : out) {
                if (m.replaceAll("§.", "").contains(fragment)) {
                    return true;
                }
            }
            return false;
        }
    }

    static TestPlayer online(String name) {
        TestPlayer p = new TestPlayer(name);
        Bukkit.PLAYERS.put(p.getUniqueId(), p);
        Bukkit.ENTITIES.put(p.getUniqueId(), p);
        return p;
    }

    static ItemStack item(org.bukkit.Material m, int n) {
        return new ItemStack(m, n);
    }

    /** A map-backed ConfigurationSection so config parsing can be tested with real values. */
    static class MapSection implements ConfigurationSection {
        final Map<String, Object> values = new java.util.LinkedHashMap<>();

        MapSection put(String path, Object v) {
            values.put(path, v);
            return this;
        }

        @Override public boolean getBoolean(String path) { return getBoolean(path, false); }
        @Override public boolean getBoolean(String path, boolean def) { return values.get(path) instanceof Boolean b ? b : def; }
        @Override public int getInt(String path) { return getInt(path, 0); }
        @Override public int getInt(String path, int def) { return values.get(path) instanceof Number n ? n.intValue() : def; }
        @Override public long getLong(String path) { return getLong(path, 0L); }
        @Override public long getLong(String path, long def) { return values.get(path) instanceof Number n ? n.longValue() : def; }
        @Override public double getDouble(String path) { return getDouble(path, 0.0); }
        @Override public double getDouble(String path, double def) { return values.get(path) instanceof Number n ? n.doubleValue() : def; }
        @Override public String getString(String path) { return getString(path, null); }
        @Override public String getString(String path, String def) { return values.get(path) instanceof String s ? s : def; }
        @Override @SuppressWarnings("unchecked") public List<String> getStringList(String path) {
            return values.get(path) instanceof List<?> l ? (List<String>) l : new ArrayList<>();
        }
        @Override @SuppressWarnings("unchecked") public List<Integer> getIntegerList(String path) {
            return values.get(path) instanceof List<?> l ? (List<Integer>) l : new ArrayList<>();
        }
        @Override public List<Double> getDoubleList(String path) { return new ArrayList<>(); }
        @Override public List<?> getList(String path) { return new ArrayList<>(); }
        @Override public List<Map<?, ?>> getMapList(String path) { return new ArrayList<>(); }
        @Override public ItemStack getItemStack(String path) { return null; }
        @Override public ItemStack getItemStack(String path, ItemStack def) { return def; }
        @Override public ConfigurationSection getConfigurationSection(String path) {
            MapSection sub = new MapSection();
            String prefix = path + ".";
            boolean any = false;
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    sub.values.put(e.getKey().substring(prefix.length()), e.getValue());
                    any = true;
                }
            }
            return any ? sub : null;
        }
        @Override public ConfigurationSection createSection(String path) { return new MapSection(); }
        @Override public void set(String path, Object value) { values.put(path, value); }
        @Override public Set<String> getKeys(boolean deep) {
            Set<String> keys = new LinkedHashSet<>();
            for (String k : values.keySet()) {
                keys.add(deep || !k.contains(".") ? k : k.substring(0, k.indexOf('.')));
            }
            return keys;
        }
        @Override public boolean isSet(String path) { return values.containsKey(path); }
        @Override public boolean contains(String path) { return values.containsKey(path); }
    }
}
