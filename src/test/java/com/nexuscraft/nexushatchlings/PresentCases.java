package com.nexuscraft.nexushatchlings;

import com.nexuscraft.nexuswaiting.WaitingApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexushatchlings.WaitingBridgeTest.expect;

/** The cases that need NexusWaiting's API class; only ever loaded when it is on the classpath. */
final class PresentCases {

    static final class Recorder implements WaitingApi {
        final List<String> calls = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        boolean explode;

        @Override
        public void set(UUID player, String key, String label, long readyAtMillis) {
            if (explode) {
                throw new IllegalStateException("boom");
            }
            calls.add("set|" + player + "|" + key + "|" + label);
            times.add(readyAtMillis);
        }

        @Override
        public void clear(UUID player, String key) {
            if (explode) {
                throw new IllegalStateException("boom");
            }
            calls.add("clear|" + player + "|" + key);
        }

        @Override
        public boolean has(UUID player, String key) {
            return false;
        }
    }

    static void run() {
        List<String> warns = new ArrayList<>();

        // installed but not running yet: nothing happens, and it is tried again later
        WaitingBridge early = new WaitingBridge(warns::add);
        UUID owner = UUID.randomUUID();
        early.set(owner, "k", "l", 1L);
        expect(warns.isEmpty(), "present: not registered yet is quiet");
        Recorder api = new Recorder();
        Bukkit.getServicesManager().register(WaitingApi.class, api, new WaitingBridgeTest.TestPlugin(), ServicePriority.Normal);
        early.set(owner, "k", "l", 7L);
        expect(api.calls.equals(List.of("set|" + owner + "|k|l")) && api.times.get(0) == 7L, "present: found once it is running: " + api.calls);
        early.clear(owner, "k");
        expect(api.calls.size() == 2 && api.calls.get(1).equals("clear|" + owner + "|k"), "present: clear passes through");
        api.calls.clear();
        api.times.clear();

        WaitingBridgeTest.TestPlugin plugin = new WaitingBridgeTest.TestPlugin();
        HatchlingManager m = WaitingBridgeTest.manager(new WaitingBridge(warns::add), plugin);
        Mob mob = new Mob();
        mob.stubSetType(EntityType.WOLF);
        long before = System.currentTimeMillis();
        m.tame(mob, owner);
        long after = System.currentTimeMillis();
        String key = "hatchling:" + mob.getUniqueId();
        expect(api.calls.size() == 1 && api.calls.get(0).equals("set|" + owner + "|" + key + "|Your tamed wolf has finished growing"), "tame: one timer, named for the creature: " + api.calls);
        long day = 24L * 60 * 60 * 1000;
        long total = m.totalDurationMillis();
        expect(total >= day && api.times.get(0) >= before + total && api.times.get(0) <= after + total, "tame: ready when growth finishes: " + api.times);

        // the periodic reapply re-registers the same timer (NexusWaiting ignores an identical one)
        api.calls.clear();
        api.times.clear();
        long readyFirst = m.progressFraction(mob) < 1.0 ? 1 : 0;
        m.reapplyOne(mob);
        expect(api.calls.size() == 1 && api.calls.get(0).startsWith("set|" + owner + "|" + key), "reapply: re-sends the timer while still growing: " + api.calls + readyFirst);

        // fully grown: nothing is sent, so a timer the owner cleared does not come back
        api.calls.clear();
        mob.getPersistentDataContainer().set(new HatchlingKeys(plugin).tamedAt, HatchlingKeys.LONG, System.currentTimeMillis() - total - 1000);
        m.reapplyOne(mob);
        expect(api.calls.isEmpty(), "reapply: nothing once fully grown: " + api.calls);

        // an owner but no tame time (should not happen): nothing is sent, nothing breaks
        Mob odd = new Mob();
        odd.getPersistentDataContainer().set(new HatchlingKeys(plugin).owner, HatchlingKeys.STRING, owner.toString());
        api.calls.clear();
        m.reapplyOne(odd);
        expect(api.calls.isEmpty(), "reapply: no tame time, no timer: " + api.calls);

        // release takes it off the list (and uses the owner before forgetting it)
        api.calls.clear();
        m.release(mob);
        expect(api.calls.equals(List.of("clear|" + owner + "|" + key)) && !m.isTame(mob), "release: timer cleared: " + api.calls);
        api.calls.clear();
        m.release(mob);
        expect(api.calls.isEmpty(), "release: an untamed creature clears nothing");

        // death
        Mob mob2 = new Mob();
        m.tame(mob2, owner);
        api.calls.clear();
        HatchlingListener listener = new HatchlingListener(new HatchlingConfig(plugin), m, new HatchlingKeys(plugin));
        listener.onDeath(new EntityDeathEvent(mob2));
        expect(api.calls.equals(List.of("clear|" + owner + "|hatchling:" + mob2.getUniqueId())), "death: a hatchling's death clears its timer: " + api.calls);
        api.calls.clear();
        listener.onDeath(new EntityDeathEvent(new Mob()));
        listener.onDeath(new EntityDeathEvent(null));
        expect(api.calls.isEmpty(), "death: other creatures and null are ignored");

        // an API that misbehaves: one warning, no crash, hatchlings unaffected
        api.explode = true;
        List<String> w2 = new ArrayList<>();
        HatchlingManager bad = WaitingBridgeTest.manager(new WaitingBridge(w2::add), plugin);
        Mob mob3 = new Mob();
        bad.tame(mob3, owner);
        bad.reapplyOne(mob3);
        bad.release(mob3);
        expect(bad.isTame(mob3) == false && w2.size() == 1 && w2.get(0).contains("NexusWaiting"), "boom: one warning, hatchling logic unaffected: " + w2);
    }
}
