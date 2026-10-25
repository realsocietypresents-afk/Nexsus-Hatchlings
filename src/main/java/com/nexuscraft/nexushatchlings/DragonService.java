package com.nexuscraft.nexushatchlings;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a dragon does after it exists: it is one real Ender Dragon entity at every size, with its AI switched off and
 * every move made by this class teleporting it a short step. A hatchling only walks, a fledgling flies and can be ridden, an
 * adult also answers its owner's command word with an attack. Colour, owner, word and size are all kept on the entity itself
 * (its persistent data), so nothing here needs a save file; the only things held in memory are what it is doing right now.
 */
public final class DragonService {

    public enum MountResult { OK, DISABLED, WILD, NOT_OWNER, TOO_YOUNG, OCCUPIED }

    public enum WordResult { OK, INVALID, TAKEN, NOT_OWNER }

    public enum Status { STARTED, DISABLED, NOT_OWNER, NOT_ADULT, BUSY, WRONG_WORLD, FAR_AWAY, NO_TARGET }

    public enum StayResult { OK, NOT_OWNER, RIDDEN, WRONG_WORLD }

    /** A place a dragon was told to wait. */
    public record Spot(String world, double x, double y, double z) {

        String encode() {
            return world + "|" + x + "|" + y + "|" + z;
        }

        static Spot decode(String raw) {
            if (raw == null) {
                return null;
            }
            String[] parts = raw.split("\\|");
            if (parts.length < 4) {
                return null;
            }
            try {
                int n = parts.length;
                String world = String.join("|", java.util.Arrays.copyOfRange(parts, 0, n - 3));
                return new Spot(world, Double.parseDouble(parts[n - 3]), Double.parseDouble(parts[n - 2]), Double.parseDouble(parts[n - 1]));
            } catch (NumberFormatException bad) {
                return null;
            }
        }

        V3 point() {
            return new V3(x, y, z);
        }
    }

    /** What came of an attack order. {@code names} lists who it will go after when it started. */
    public record OrderResult(Status status, Targeting.Reason reason, List<String> names) {
    }

    private static final class Ride {
        final UUID rider;
        boolean moving;

        Ride(UUID rider) {
            this.rider = rider;
        }
    }

    private static final class Attack {
        final List<UUID> targets;
        final long endsAt;
        long nextStrike;

        Attack(List<UUID> targets, long endsAt, long nextStrike) {
            this.targets = targets;
            this.endsAt = endsAt;
            this.nextStrike = nextStrike;
        }
    }

    static final String TEAM_PREFIX = "nxd_";

    private final JavaPlugin plugin;
    private final HatchlingConfig hatch;
    private final DragonConfig cfg;
    private final HatchlingKeys keys;
    private final HatchlingManager manager;
    private final HousesBridge houses;
    private final Random random;

    private final Map<UUID, EnderDragon> dragons = new LinkedHashMap<>();
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Attack> attacks = new HashMap<>();
    private final Map<UUID, V3> homes = new HashMap<>();
    private final Map<UUID, V3> goals = new HashMap<>();
    private final Map<UUID, DragonStage> lastStage = new HashMap<>();
    private final Map<UUID, Spot> stays = new HashMap<>();
    private final Map<UUID, List<UUID>> formations = new HashMap<>();
    private DragonRoster roster;
    private DragonModels models;
    private long ticks;

    public DragonService(JavaPlugin plugin, HatchlingConfig hatch, DragonConfig cfg, HatchlingKeys keys,
                         HatchlingManager manager, HousesBridge houses, Random random) {
        this.plugin = plugin;
        this.hatch = hatch;
        this.cfg = cfg;
        this.keys = keys;
        this.manager = manager;
        this.houses = houses;
        this.random = random;
    }

    /** Gives the service the saved list of who owns which dragon (set once at start-up). Without it the limit counts only loaded dragons. */
    public void setRoster(DragonRoster roster) {
        this.roster = roster;
    }

    /** Gives the service the drawer of block-built dragons (set once at start-up). Without it dragons are the real tinted model. */
    public void setModels(DragonModels models) {
        this.models = models;
    }

    public DragonRoster roster() {
        return roster;
    }

    public DragonModels models() {
        return models;
    }

    // ------------------------------------------------------------------ who is a dragon

    /** True for an Ender Dragon this plugin set up (an egg hatched it, or a crystal tamed it). */
    public boolean isDragon(Entity entity) {
        return entity instanceof EnderDragon
                && entity.getPersistentDataContainer().has(keys.dragonMark, HatchlingKeys.INTEGER);
    }

    /** Turns a clicked hitbox part into its dragon, and returns null for anything that is not one of ours. */
    public EnderDragon dragonOf(Entity clicked) {
        Entity entity = clicked;
        if (entity instanceof org.bukkit.entity.EnderDragonPart part) {
            entity = part.getParent();
        }
        return isDragon(entity) ? (EnderDragon) entity : null;
    }

    /** Who owns this dragon, or null when it is wild. */
    public UUID ownerOf(EnderDragon dragon) {
        return manager.ownerOf(dragon);
    }

    /** The loaded dragon with this id, or null. */
    public EnderDragon byId(UUID id) {
        EnderDragon d = dragons.get(id);
        return d != null && d.isValid() ? d : null;
    }

    public List<EnderDragon> loaded() {
        return new ArrayList<>(dragons.values());
    }

    public List<EnderDragon> dragonsOf(UUID owner) {
        List<EnderDragon> out = new ArrayList<>();
        for (EnderDragon d : dragons.values()) {
            if (owner.equals(manager.ownerOf(d))) {
                out.add(d);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ creating and adopting

    /** A new wild baby, as an egg makes. */
    public EnderDragon spawnBaby(Location where) {
        World world = where.getWorld();
        EnderDragon baby = world.spawn(where, EnderDragon.class);
        AttributeInstance scale = baby.getAttribute(Attribute.GENERIC_SCALE);
        if (scale != null) {
            scale.setBaseValue(hatch.startScale());
        }
        adopt(baby, null);
        return baby;
    }

    /**
     * Takes an Ender Dragon under this class's care: switches its own AI off, marks it, gives it a colour and (if it has an owner)
     * a command word. Safe to call again for a dragon that is already managed.
     */
    public void adopt(EnderDragon dragon, UUID owner) {
        prepare(dragon);
        var pdc = dragon.getPersistentDataContainer();
        pdc.set(keys.dragonMark, HatchlingKeys.INTEGER, 1);
        if (DragonColor.byId(pdc.get(keys.dragonColor, HatchlingKeys.STRING)) == null) {
            pdc.set(keys.dragonColor, HatchlingKeys.STRING, DragonColor.random(random).id());
        }
        if (owner != null && wordOf(dragon) == null) {
            pdc.set(keys.dragonWord, HatchlingKeys.STRING, CommandWords.pickDefault(wordsOfOwner(owner, dragon), random));
        }
        UUID id = dragon.getUniqueId();
        dragons.put(id, dragon);
        if (owner != null) {
            if (roster != null) {
                roster.add(owner, id);
            }
            Spot kept = Spot.decode(pdc.get(keys.dragonStay, HatchlingKeys.STRING));
            if (kept != null) {
                stays.put(id, kept);
            }
        } else {
            pdc.remove(keys.dragonStay);
            stays.remove(id);
        }
        applyLook(dragon);
    }

    private void prepare(EnderDragon dragon) {
        dragon.setAI(false);
        dragon.setRemoveWhenFarAway(false);
        dragon.setPersistent(true);
        dragon.setTarget(null);
        dragon.setPhase(EnderDragon.Phase.HOVER);
    }

    /** Called when its owner lets it go: it forgets its word and any order, and stands where it is as a wild dragon. */
    public void onRelease(EnderDragon dragon) {
        UUID id = dragon.getUniqueId();
        dragon.getPersistentDataContainer().remove(keys.dragonWord);
        dragon.getPersistentDataContainer().remove(keys.dragonStay);
        stays.remove(id);
        attacks.remove(id);
        Ride ride = rides.remove(id);
        if (ride != null) {
            for (Entity passenger : new ArrayList<>(dragon.getPassengers())) {
                dragon.removePassenger(passenger);
                if (models != null && passenger instanceof Player rider) {
                    models.showTo(id, rider);
                }
            }
        }
        lastStage.remove(id);
        if (roster != null) {
            roster.remove(id);
        }
    }

    /** A dragon died: it is off the roster for good and its blocks go with it. */
    public void onDeath(Entity entity) {
        if (!isDragon(entity)) {
            return;
        }
        if (roster != null) {
            roster.remove(entity.getUniqueId());
        }
        forget(entity.getUniqueId());
    }

    /** Called when the plugin switches off: the block-built bodies are taken down and the roster is saved. */
    public void shutdown() {
        if (models != null) {
            models.removeAll();
        }
        if (roster != null) {
            roster.flush();
        }
    }

    // ------------------------------------------------------------------ colour, name, word

    public DragonColor colorOf(EnderDragon dragon) {
        DragonColor color = DragonColor.byId(dragon.getPersistentDataContainer().get(keys.dragonColor, HatchlingKeys.STRING));
        return color == null ? DragonColor.CRIMSON : color;
    }

    public void setColor(EnderDragon dragon, DragonColor color) {
        DragonColor old = colorOf(dragon);
        dragon.getPersistentDataContainer().set(keys.dragonColor, HatchlingKeys.STRING, color.id());
        String name = dragon.getCustomName();
        if (name != null && name.equals(nameFor(old))) {
            dragon.setCustomName(nameFor(color));
        }
        applyLook(dragon);
    }

    static String nameFor(DragonColor color) {
        return "§" + color.chatCode() + color.display() + " Dragon";
    }

    public String wordOf(EnderDragon dragon) {
        return dragon.getPersistentDataContainer().get(keys.dragonWord, HatchlingKeys.STRING);
    }

    private Set<String> wordsOfOwner(UUID owner, EnderDragon except) {
        Set<String> words = new HashSet<>();
        for (EnderDragon d : dragons.values()) {
            if (d != except && owner.equals(manager.ownerOf(d))) {
                String w = wordOf(d);
                if (w != null) {
                    words.add(w.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        return words;
    }

    /** Changes a dragon's command word. Only its owner may, and the word must be free among that owner's dragons. */
    public WordResult setWord(EnderDragon dragon, UUID by, String word) {
        UUID owner = manager.ownerOf(dragon);
        if (owner == null || !owner.equals(by)) {
            return WordResult.NOT_OWNER;
        }
        if (!CommandWords.valid(word)) {
            return WordResult.INVALID;
        }
        String clean = CommandWords.normalize(word);
        if (wordsOfOwner(owner, dragon).contains(clean)) {
            return WordResult.TAKEN;
        }
        dragon.getPersistentDataContainer().set(keys.dragonWord, HatchlingKeys.STRING, clean);
        return WordResult.OK;
    }

    /** The one of this owner's loaded dragons with this word, or null. */
    public EnderDragon byWord(UUID owner, String word) {
        String wanted = CommandWords.normalize(word);
        if (wanted == null) {
            return null;
        }
        for (EnderDragon d : dragonsOf(owner)) {
            if (wanted.equals(wordOf(d))) {
                return d;
            }
        }
        return null;
    }

    private void applyLook(EnderDragon dragon) {
        DragonColor color = colorOf(dragon);
        String name = dragon.getCustomName();
        if (name == null) {
            dragon.setCustomName(nameFor(color));
            dragon.setCustomNameVisible(true);
        }
        Scoreboard board = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = dragon.getUniqueId().toString();
        boolean modelled = cfg.model && models != null;
        if (board != null) {
            for (DragonColor other : DragonColor.values()) {
                Team team = board.getTeam(TEAM_PREFIX + other.id());
                if (team != null && (modelled || other != color)) {
                    team.removeEntry(entry);
                }
            }
        }
        if (modelled) {
            // The real dragon only keeps the hitbox: what players see is the coloured blocks.
            dragon.setGlowing(false);
            dragon.setInvisible(true);
            models.ensure(dragon, color, scaleOf(dragon), ticks);
            return;
        }
        dragon.setInvisible(false);
        if (models != null) {
            models.remove(dragon.getUniqueId());
        }
        if (cfg.glow && board != null) {
            Team team = board.getTeam(TEAM_PREFIX + color.id());
            if (team == null) {
                team = board.registerNewTeam(TEAM_PREFIX + color.id());
            }
            ChatColor chat = ChatColor.getByChar(color.chatCode());
            if (chat != null) {
                team.setColor(chat);
            }
            team.addEntry(entry);
            dragon.setGlowing(true);
        } else {
            dragon.setGlowing(false);
        }
    }

    // ------------------------------------------------------------------ size and stage

    /** How far along growth is, 0 to 1. A tamed dragon grows by the clock; a wild one is judged by the size it already is. */
    public double progressOf(EnderDragon dragon) {
        if (manager.isTame(dragon)) {
            return manager.progressFraction(dragon);
        }
        AttributeInstance scale = dragon.getAttribute(Attribute.GENERIC_SCALE);
        double span = hatch.maxScale() - hatch.startScale();
        if (scale == null || span <= 1.0e-9) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (scale.getBaseValue() - hatch.startScale()) / span));
    }

    public DragonStage stageOf(EnderDragon dragon) {
        return DragonStage.of(progressOf(dragon), cfg.flyAt, cfg.adultAt);
    }

    private double scaleOf(EnderDragon dragon) {
        AttributeInstance scale = dragon.getAttribute(Attribute.GENERIC_SCALE);
        return scale == null ? 1.0 : scale.getBaseValue();
    }

    // ------------------------------------------------------------------ the scan (slow) and the tick (fast)

    /** Finds dragons that loaded since last time, forgets ones that are gone, and tells owners when a dragon reaches a new stage. */
    public void scan() {
        for (World world : Bukkit.getWorlds()) {
            for (EnderDragon d : world.getEntitiesByClass(EnderDragon.class)) {
                if (dragons.containsKey(d.getUniqueId()) || d.isDead()) {
                    // (a dragon that is dying stays in the world for its whole death animation: it is not picked up again)
                    continue;
                }
                if (isDragon(d)) {
                    adopt(d, manager.ownerOf(d));
                } else if (manager.isTame(d)) {
                    adopt(d, manager.ownerOf(d));
                }
            }
        }
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, EnderDragon> e : dragons.entrySet()) {
            if (!e.getValue().isValid() || e.getValue().isDead()) {
                gone.add(e.getKey());
                if (e.getValue().isDead() && roster != null) {
                    roster.remove(e.getKey());
                }
            }
        }
        for (UUID id : gone) {
            forget(id);
        }
        for (EnderDragon d : new ArrayList<>(dragons.values())) {
            announceStage(d);
            UUID owner = manager.ownerOf(d);
            if (owner != null && roster != null) {
                roster.add(owner, d.getUniqueId());
            }
        }
        if (roster != null) {
            roster.prune(cfg.staleDays * 86_400_000L);
            roster.flush();
        }
    }

    private void forget(UUID id) {
        dragons.remove(id);
        rides.remove(id);
        attacks.remove(id);
        homes.remove(id);
        goals.remove(id);
        lastStage.remove(id);
        stays.remove(id);
        if (models != null) {
            models.remove(id);
        }
    }

    private void announceStage(EnderDragon d) {
        UUID id = d.getUniqueId();
        DragonStage now = stageOf(d);
        DragonStage before = lastStage.put(id, now);
        if (before == null || before == now || now.ordinal() < before.ordinal()) {
            return;
        }
        UUID owner = manager.ownerOf(d);
        Player p = owner == null ? null : Bukkit.getPlayer(owner);
        if (p == null || !p.isOnline()) {
            return;
        }
        String word = wordOf(d);
        if (now == DragonStage.FLEDGLING) {
            p.sendMessage(DragonText.PREFIX + "Your " + colorOf(d).display().toLowerCase() + " dragon has grown wings. It can fly now: "
                    + "right-click it to climb on.");
        } else if (now == DragonStage.ADULT) {
            p.sendMessage(DragonText.PREFIX + "Your " + colorOf(d).display().toLowerCase() + " dragon is fully grown. Its word is "
                    + (word == null ? "(none)" : word) + ": say /dragon " + (word == null ? "<word>" : word) + " and it will defend you.");
        }
    }

    /** One fast step: every managed dragon moves, rides or fights. */
    public void tick() {
        ticks += cfg.moveIntervalTicks;
        buildFormations();
        for (EnderDragon d : new ArrayList<>(dragons.values())) {
            if (!d.isValid() || d.isDead()) {
                continue;
            }
            try {
                tickOne(d);
                if (models != null && cfg.model) {
                    UUID id = d.getUniqueId();
                    Ride ride = rides.get(id);
                    boolean flying = (ride != null && ride.moving) || attacks.containsKey(id);
                    models.update(d, colorOf(d), scaleOf(d), stageOf(d).canFly(), flying, ticks);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("A dragon could not be moved this tick: " + e);
            }
        }
    }

    /** The coloured dust around each dragon. */
    public void aura() {
        if (!cfg.aura) {
            return;
        }
        for (EnderDragon d : new ArrayList<>(dragons.values())) {
            Location at = d.getLocation();
            if (!d.isValid() || at == null || at.getWorld() == null) {
                continue;
            }
            DragonColor c = colorOf(d);
            double size = Math.max(0.5, Math.min(6.0, scaleOf(d) * 4.0));
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(c.red(), c.green(), c.blue()), 1.5f);
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, size * 0.3, 0), 8, size * 0.5, size * 0.25, size * 0.5, 0.0, dust);
        }
    }

    private double dt() {
        return cfg.moveIntervalTicks / 20.0;
    }

    private void tickOne(EnderDragon d) {
        Location at = d.getLocation();
        if (at == null || at.getWorld() == null) {
            return;
        }
        UUID id = d.getUniqueId();
        Ride ride = rides.get(id);
        if (ride != null) {
            rideTick(d, ride, at);
            return;
        }
        Attack attack = attacks.get(id);
        if (attack != null) {
            attackTick(d, attack, at);
            return;
        }
        UUID owner = manager.ownerOf(d);
        if (owner == null) {
            wildTick(d, at);
        } else {
            followTick(d, owner, at);
        }
    }

    // ------------------------------------------------------------------ terrain

    private Terrain terrain(World world) {
        return (x, y, z) -> {
            if (y < world.getMinHeight()) {
                return true;
            }
            if (y >= world.getMaxHeight()) {
                return false;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return true;
            }
            Block block = world.getBlockAt(x, y, z);
            return block.getType().isSolid() || block.isLiquid();
        };
    }

    private int headroom(EnderDragon d) {
        return Math.max(1, (int) Math.ceil(scaleOf(d) * 4.0));
    }

    private static V3 at(Location l) {
        return new V3(l.getX(), l.getY(), l.getZ());
    }

    private void moveTo(EnderDragon d, Location from, V3 to, double yaw, boolean carry) {
        Location next = new Location(from.getWorld(), to.x(), to.y(), to.z(), (float) yaw, from.getPitch());
        if (carry) {
            d.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        } else {
            d.teleport(next);
        }
    }

    // ------------------------------------------------------------------ walking, flying, following

    /** One step along the ground towards a point; false if the way is blocked (a wall, a drop, an unloaded chunk). */
    private boolean walkToward(EnderDragon d, Location from, V3 goal, double speed) {
        V3 cur = at(from);
        V3 flat = new V3(goal.x(), cur.y(), goal.z());
        if (cur.distance(flat) < 0.05) {
            return true;
        }
        V3 next = DragonMath.stepToward(cur, flat, speed * dt());
        double gy = DragonMath.groundY(terrain(from.getWorld()), (int) Math.floor(next.x()), (int) Math.floor(next.z()),
                (int) Math.floor(cur.y()), 1, 3, headroom(d));
        if (Double.isNaN(gy)) {
            return false;
        }
        moveTo(d, from, new V3(next.x(), gy, next.z()), DragonMath.dragonYaw(DragonMath.yawToward(cur, flat), cfg.yawOffset), false);
        return true;
    }

    private void flyToward(EnderDragon d, Location from, V3 goal, double speed, boolean carry) {
        V3 cur = at(from);
        V3 next = DragonMath.stepToward(cur, goal, speed * dt());
        World world = from.getWorld();
        double y = DragonMath.clampY(next.y(), world.getMinHeight() + 2, world.getMaxHeight() - 4);
        double yaw = cur.distance(new V3(goal.x(), cur.y(), goal.z())) > 0.5
                ? DragonMath.dragonYaw(DragonMath.yawToward(cur, goal), cfg.yawOffset) : from.getYaw();
        moveTo(d, from, new V3(next.x(), y, next.z()), yaw, carry);
    }

    /**
     * Lets a dragon sink to the ground where it stands (a walker drops straight down; a flyer glides down). A flyer never sinks
     * below {@code hoverFloor}, so it does not plunge to the ground while its owner is up in the air.
     */
    private void settle(EnderDragon d, Location from, boolean flyer, double hoverFloor) {
        double gy = DragonMath.groundY(terrain(from.getWorld()), from.getBlockX(), from.getBlockZ(), from.getBlockY(), 1,
                flyer ? 64 : 6, headroom(d));
        if (Double.isNaN(gy)) {
            return;
        }
        double y = from.getY();
        if (y < gy) {
            y = gy;
        } else if (flyer && y <= hoverFloor) {
            return;
        } else {
            y = DragonMath.descend(y, Math.max(gy, flyer ? hoverFloor : gy), (flyer ? cfg.descendSpeed : 20.0) * dt());
        }
        if (Math.abs(y - from.getY()) < 0.01) {
            return;
        }
        moveTo(d, from, new V3(from.getX(), y, from.getZ()), from.getYaw(), false);
    }

    private void settle(EnderDragon d, Location from, boolean flyer) {
        settle(d, from, flyer, Double.NEGATIVE_INFINITY);
    }

    /** Works out, once a tick, which dragons of each owner are following (so each can take its own place around the owner). */
    private void buildFormations() {
        formations.clear();
        if (!cfg.formation) {
            return;
        }
        for (EnderDragon d : dragons.values()) {
            if (!d.isValid()) {
                continue;
            }
            UUID owner = manager.ownerOf(d);
            UUID id = d.getUniqueId();
            if (owner == null || stays.containsKey(id) || rides.containsKey(id)) {
                continue;
            }
            Player p = Bukkit.getPlayer(owner);
            Location dl = d.getLocation();
            Location pl = p == null || !p.isOnline() ? null : p.getLocation();
            if (dl == null || pl == null || dl.getWorld() == null || pl.getWorld() == null
                    || !dl.getWorld().getName().equals(pl.getWorld().getName())) {
                continue;
            }
            formations.computeIfAbsent(owner, k -> new ArrayList<>()).add(id);
        }
    }

    /** Where this dragon stands in relation to its owner, or null when it is the only one following (then it simply comes to them). */
    private V3 slotOffset(UUID owner, UUID dragon) {
        List<UUID> following = formations.get(owner);
        if (following == null || following.size() < 2) {
            return null;
        }
        return Formation.slotFor(following, dragon, cfg.formationSpacing, cfg.formationMinRadius);
    }

    /** Where a dragon lands when it has to be brought to its owner: its own place in the ring when that is open, otherwise the owner's spot. */
    private V3 arrivalSpot(EnderDragon d, Location there, boolean fly, V3 off) {
        World world = there.getWorld();
        if (off != null) {
            double x = there.getX() + off.x();
            double z = there.getZ() + off.z();
            double y = fly ? there.getY() + 2.0 + off.y()
                    : DragonMath.groundY(terrain(world), (int) Math.floor(x), (int) Math.floor(z), there.getBlockY(), 1, 4, headroom(d));
            boolean open = fly ? DragonMath.clear(terrain(world), new V3(x, y, z), cfg.rideClearance) : !Double.isNaN(y);
            if (open) {
                return new V3(x, y, z);
            }
        }
        double y = fly ? there.getY() + 2.0
                : DragonMath.groundY(terrain(world), there.getBlockX(), there.getBlockZ(), there.getBlockY(), 1, 4, headroom(d));
        return new V3(there.getX(), Double.isNaN(y) ? there.getY() + (fly ? 2.0 : 0.0) : y, there.getZ());
    }

    private void followTick(EnderDragon d, UUID ownerId, Location from) {
        Spot waiting = stays.get(d.getUniqueId());
        if (waiting != null) {
            stayTick(d, waiting, from);
            return;
        }
        DragonStage stage = stageOf(d);
        Player owner = Bukkit.getPlayer(ownerId);
        Location there = owner == null || !owner.isOnline() ? null : owner.getLocation();
        if (!hatch.followOwner() || there == null || there.getWorld() == null || from.getWorld() == null
                || !there.getWorld().getName().equals(from.getWorld().getName())) {
            settle(d, from, stage.canFly());
            return;
        }
        boolean fly = stage.canFly();
        V3 off = slotOffset(ownerId, d.getUniqueId());
        double distance = from.distance(there);
        if (distance > hatch.teleportToOwnerDistanceBlocks()) {
            V3 spot = arrivalSpot(d, there, fly, off);
            moveTo(d, from, spot, from.getYaw(), false);
            return;
        }
        double goalX = there.getX() + (off == null ? 0.0 : off.x());
        double goalZ = there.getZ() + (off == null ? 0.0 : off.z());
        double liftY = fly && off != null ? off.y() : 0.0;
        V3 goal = new V3(goalX, there.getY() + (fly ? 2.0 : 0.0) + liftY, goalZ);
        boolean far = off == null ? distance > hatch.followRadiusBlocks()
                : Math.hypot(from.getX() - goalX, from.getZ() - goalZ) > cfg.formationTolerance;
        if (far) {
            if (fly) {
                flyToward(d, from, goal, cfg.followFlySpeed, false);
            } else if (!walkToward(d, from, new V3(goalX, there.getY(), goalZ), cfg.walkSpeed)) {
                settle(d, from, false);
            }
        } else {
            settle(d, from, fly, there.getY() + liftY);
        }
    }

    /** A dragon told to stay: it goes back to its spot if it was moved, and otherwise just stands (or hovers) there. */
    private void stayTick(EnderDragon d, Spot spot, Location from) {
        if (from.getWorld() == null || !spot.world().equals(from.getWorld().getName())) {
            follow(d);
            return;
        }
        boolean fly = stageOf(d).canFly();
        V3 here = at(from);
        V3 goal = spot.point();
        double flat = Math.hypot(here.x() - goal.x(), here.z() - goal.z());
        if (flat > STAY_RADIUS) {
            if (fly) {
                flyToward(d, from, goal, cfg.followFlySpeed, false);
            } else if (!walkToward(d, from, goal, cfg.walkSpeed)) {
                settle(d, from, false);
            }
            return;
        }
        settle(d, from, fly, goal.y());
    }

    /** How close to its spot a staying dragon has to be before it is content. */
    static final double STAY_RADIUS = 1.0;

    private void wildTick(EnderDragon d, Location from) {
        UUID id = d.getUniqueId();
        V3 home = homes.computeIfAbsent(id, k -> at(from));
        if (!cfg.wander) {
            settle(d, from, false);
            return;
        }
        V3 goal = goals.get(id);
        V3 here = at(from);
        if (goal != null && here.distance(new V3(goal.x(), here.y(), goal.z())) < 0.8) {
            goals.remove(id);
            goal = null;
        }
        if (goal == null) {
            if (random.nextInt(20) == 0) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = random.nextDouble() * cfg.wanderRadius;
                goal = new V3(home.x() + Math.cos(angle) * radius, home.y(), home.z() + Math.sin(angle) * radius);
                goals.put(id, goal);
            } else {
                settle(d, from, false);
                return;
            }
        }
        if (!walkToward(d, from, goal, cfg.walkSpeed)) {
            goals.remove(id);
            settle(d, from, false);
        }
    }

    // ------------------------------------------------------------------ riding

    public MountResult mount(Player player, EnderDragon dragon) {
        if (!cfg.rideEnabled) {
            return MountResult.DISABLED;
        }
        UUID owner = manager.ownerOf(dragon);
        if (owner == null) {
            return MountResult.WILD;
        }
        if (cfg.rideOwnerOnly && !owner.equals(player.getUniqueId())) {
            return MountResult.NOT_OWNER;
        }
        if (!stageOf(dragon).canFly()) {
            return MountResult.TOO_YOUNG;
        }
        if (!dragon.getPassengers().isEmpty() || !dragon.addPassenger(player)) {
            return MountResult.OCCUPIED;
        }
        attacks.remove(dragon.getUniqueId());
        clearStay(dragon);
        rides.put(dragon.getUniqueId(), new Ride(player.getUniqueId()));
        if (models != null) {
            models.hideFrom(dragon.getUniqueId(), player);
        }
        return MountResult.OK;
    }

    /** The dragon this player is sitting on, or null. */
    public EnderDragon riddenBy(Player player) {
        Entity vehicle = player.getVehicle();
        return isDragon(vehicle) ? (EnderDragon) vehicle : null;
    }

    /** Right-click while riding: starts or stops the flight. Returns the new state (true = flying), or null if the player is not riding one of ours. */
    public Boolean toggleRide(Player player) {
        EnderDragon dragon = riddenBy(player);
        if (dragon == null) {
            return null;
        }
        Ride ride = rides.computeIfAbsent(dragon.getUniqueId(), k -> new Ride(player.getUniqueId()));
        ride.moving = !ride.moving;
        return ride.moving;
    }

    private void rideTick(EnderDragon d, Ride ride, Location from) {
        UUID id = d.getUniqueId();
        Player rider = Bukkit.getPlayer(ride.rider);
        boolean seated = rider != null && rider.isOnline() && d.getPassengers().contains(rider);
        if (seated && (!cfg.rideEnabled || !stageOf(d).canFly())) {
            seated = false;
        }
        if (!seated) {
            rides.remove(id);
            if (rider != null) {
                d.removePassenger(rider);
                if (models != null) {
                    models.showTo(id, rider);
                }
            }
            if (rider != null && rider.isOnline() && cfg.slowFallSeconds > 0 && rider.getLocation() != null
                    && rider.getLocation().getWorld() != null) {
                Location l = rider.getLocation();
                double gy = DragonMath.groundY(terrain(l.getWorld()), l.getBlockX(), l.getBlockZ(), l.getBlockY(), 0, 3, 1);
                if (Double.isNaN(gy)) {
                    rider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, cfg.slowFallSeconds * 20, 0));
                }
            }
            return;
        }
        Location look = rider.getLocation();
        double yaw = DragonMath.dragonYaw(look.getYaw(), cfg.yawOffset);
        if (!ride.moving) {
            if (Math.abs(DragonMath.normalizeYaw(yaw - from.getYaw())) > 1.0) {
                moveTo(d, from, at(from), yaw, true);
            }
            return;
        }
        V3 dir = DragonMath.lookVector(look.getYaw(), look.getPitch());
        V3 cur = at(from);
        V3 next = cur.plus(dir.times(cfg.rideSpeed * dt()));
        World world = from.getWorld();
        next = new V3(next.x(), DragonMath.clampY(next.y(), world.getMinHeight() + 2, world.getMaxHeight() - 4), next.z());
        if (!DragonMath.clear(terrain(world), next, cfg.rideClearance)) {
            moveTo(d, from, cur, yaw, true);
            return;
        }
        moveTo(d, from, next, yaw, true);
    }

    // ------------------------------------------------------------------ attack orders

    /** Whether this player may be hurt by a dragon of this owner right now. */
    private boolean eligible(UUID owner, Player p) {
        GameMode mode = p.getGameMode();
        return p.isOnline() && !p.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && !p.getUniqueId().equals(owner) && !allied(owner, p.getUniqueId());
    }

    private boolean allied(UUID owner, UUID other) {
        return cfg.useHouses && houses.allied(owner, other);
    }

    public OrderResult attack(Player owner, EnderDragon dragon, Targeting.Mode mode, String name) {
        UUID id = dragon.getUniqueId();
        if (!cfg.attackEnabled) {
            return new OrderResult(Status.DISABLED, null, List.of());
        }
        if (!owner.getUniqueId().equals(manager.ownerOf(dragon))) {
            return new OrderResult(Status.NOT_OWNER, null, List.of());
        }
        if (!stageOf(dragon).canAttack()) {
            return new OrderResult(Status.NOT_ADULT, null, List.of());
        }
        if (rides.containsKey(id)) {
            return new OrderResult(Status.BUSY, null, List.of());
        }
        Location dl = dragon.getLocation();
        Location ol = owner.getLocation();
        if (dl == null || ol == null || dl.getWorld() == null || ol.getWorld() == null
                || !dl.getWorld().getName().equals(ol.getWorld().getName())) {
            return new OrderResult(Status.WRONG_WORLD, null, List.of());
        }
        if (dl.distance(ol) > cfg.commandRange) {
            return new OrderResult(Status.FAR_AWAY, null, List.of());
        }
        List<Targeting.Candidate> everyone = new ArrayList<>();
        for (Player p : dl.getWorld().getPlayers()) {
            Location pl = p.getLocation();
            if (pl == null) {
                continue;
            }
            GameMode gm = p.getGameMode();
            boolean hurtable = !p.isDead() && (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE);
            everyone.add(new Targeting.Candidate(p.getUniqueId(), p.getName(), dl.distance(pl),
                    p.getUniqueId().equals(owner.getUniqueId()), allied(owner.getUniqueId(), p.getUniqueId()), hurtable));
        }
        Targeting.Result picked = Targeting.pick(everyone, mode, name, cfg.targetRange);
        if (!picked.ok()) {
            return new OrderResult(Status.NO_TARGET, picked.reason(), List.of());
        }
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Targeting.Candidate c : picked.targets()) {
            ids.add(c.id());
            names.add(c.name());
        }
        attacks.put(id, new Attack(ids, ticks + cfg.durationSeconds * 20L, ticks));
        dl.getWorld().playSound(dl, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.0f);
        return new OrderResult(Status.STARTED, Targeting.Reason.OK, names);
    }

    /** Calls a dragon off. Returns true if it had an order. */
    public boolean stop(EnderDragon dragon) {
        return attacks.remove(dragon.getUniqueId()) != null;
    }

    public boolean isAttacking(EnderDragon dragon) {
        return attacks.containsKey(dragon.getUniqueId());
    }

    public boolean isRidden(EnderDragon dragon) {
        return rides.containsKey(dragon.getUniqueId());
    }

    private void attackTick(EnderDragon d, Attack attack, Location from) {
        UUID id = d.getUniqueId();
        UUID ownerId = manager.ownerOf(d);
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !cfg.attackEnabled || !stageOf(d).canAttack() || ticks >= attack.endsAt) {
            attacks.remove(id);
            return;
        }
        List<Player> live = new ArrayList<>();
        for (UUID t : attack.targets) {
            Player p = Bukkit.getPlayer(t);
            if (p != null && eligible(ownerId, p) && p.getLocation() != null && p.getLocation().getWorld() != null
                    && p.getLocation().getWorld().getName().equals(from.getWorld().getName())) {
                live.add(p);
            }
        }
        if (live.isEmpty()) {
            attacks.remove(id);
            owner.sendMessage(DragonText.PREFIX + "Your dragon has nobody left to go after, and comes back to you.");
            return;
        }
        Player focus = live.get(0);
        for (Player p : live) {
            if (p.getLocation().distance(from) < focus.getLocation().distance(from)) {
                focus = p;
            }
        }
        Location fl = focus.getLocation();
        V3 hover = new V3(fl.getX(), fl.getY() + cfg.hoverHeight, fl.getZ());
        if (at(from).distance(hover) > 2.0) {
            flyToward(d, from, hover, cfg.attackSpeed, false);
        }
        if (ticks >= attack.nextStrike) {
            boolean struck = false;
            for (Player p : live) {
                if (p.getLocation().distance(d.getLocation()) <= cfg.strikeRange) {
                    breathe(d, p);
                    struck = true;
                }
            }
            if (struck) {
                attack.nextStrike = ticks + cfg.intervalTicks;
            }
        }
    }

    private void breathe(EnderDragon d, Player target) {
        Location mouth = d.getLocation().clone().add(0, Math.max(1.0, scaleOf(d) * 3.0), 0);
        Location end = target.getLocation().clone().add(0, 1.0, 0);
        World world = mouth.getWorld();
        double length = mouth.distance(end);
        int steps = (int) Math.min(40, Math.max(1, Math.floor(length)));
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            Location p = new Location(world, mouth.getX() + (end.getX() - mouth.getX()) * f,
                    mouth.getY() + (end.getY() - mouth.getY()) * f, mouth.getZ() + (end.getZ() - mouth.getZ()) * f);
            world.spawnParticle(Particle.DRAGON_BREATH, p, 2, 0.1, 0.1, 0.1);
        }
        world.playSound(mouth, Sound.ENTITY_ENDER_DRAGON_SHOOT, 2.0f, 1.0f);
        double before = target.getHealth();
        target.damage(cfg.damage, d);
        if (target.getHealth() < before && cfg.fireSeconds > 0) {
            target.setFireTicks(cfg.fireSeconds * 20);
        }
    }

    // ------------------------------------------------------------------ colour with a dye

    /** Whether this player may recolour this dragon (a wild one by anyone, a tamed one by its owner). */
    public boolean mayRecolor(Player player, EnderDragon dragon) {
        UUID owner = manager.ownerOf(dragon);
        return owner == null || owner.equals(player.getUniqueId());
    }

    /** Re-reads the config: every managed dragon is looked at again so a change to glow or the like shows up. */
    public void refresh() {
        for (EnderDragon d : new ArrayList<>(dragons.values())) {
            if (d.isValid()) {
                prepare(d);
                applyLook(d);
            }
        }
    }

    /** One line about a dragon, for /dragon list and the sneak-click. */
    public String describe(EnderDragon d) {
        Location l = d.getLocation();
        String where = l == null ? "?" : l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
        String word = wordOf(d);
        return "§" + colorOf(d).chatCode() + colorOf(d).display() + " §7dragon, " + stageOf(d).display().toLowerCase()
                + ", " + Math.round(progressOf(d) * 100.0) + "% grown, " + statusOf(d).toLowerCase() + ", at " + where
                + (word == null ? "" : ", word §f" + word);
    }

    /** What a dragon is doing: Wild, Carrying a rider, Attacking, Staying or Following. */
    public String statusOf(EnderDragon d) {
        UUID id = d.getUniqueId();
        if (manager.ownerOf(d) == null) {
            return "Wild";
        }
        if (rides.containsKey(id)) {
            return "Carrying a rider";
        }
        if (attacks.containsKey(id)) {
            return "Attacking";
        }
        return stays.containsKey(id) ? "Staying" : "Following";
    }

    // ------------------------------------------------------------------ staying and following

    public boolean isStaying(EnderDragon d) {
        return stays.containsKey(d.getUniqueId());
    }

    public Spot stayOf(EnderDragon d) {
        return stays.get(d.getUniqueId());
    }

    private void setStay(EnderDragon d, Spot spot) {
        stays.put(d.getUniqueId(), spot);
        d.getPersistentDataContainer().set(keys.dragonStay, HatchlingKeys.STRING, spot.encode());
    }

    private void clearStay(EnderDragon d) {
        stays.remove(d.getUniqueId());
        d.getPersistentDataContainer().remove(keys.dragonStay);
    }

    /** Tells a dragon to follow its owner again. Returns true if it had been told to stay. */
    public boolean follow(EnderDragon d) {
        boolean was = stays.containsKey(d.getUniqueId());
        clearStay(d);
        return was;
    }

    /** Spots of this owner's other dragons that are staying in a world (so a new one can be put clear of them). */
    private List<V3> waitingSpots(UUID owner, String world, EnderDragon except) {
        List<V3> out = new ArrayList<>();
        for (EnderDragon other : dragons.values()) {
            Spot spot = stays.get(other.getUniqueId());
            if (other != except && spot != null && spot.world().equals(world) && owner.equals(manager.ownerOf(other))) {
                out.add(spot.point());
            }
        }
        return out;
    }

    /**
     * Tells one dragon to wait where it is ({@code atPlayer} false) or where its owner stands now (true, put clear of the
     * owner's other waiting dragons so they do not end up on top of each other).
     */
    public StayResult stay(Player by, EnderDragon d, boolean atPlayer) {
        UUID owner = manager.ownerOf(d);
        if (owner == null || !owner.equals(by.getUniqueId())) {
            return StayResult.NOT_OWNER;
        }
        if (rides.containsKey(d.getUniqueId())) {
            return StayResult.RIDDEN;
        }
        Location dl = d.getLocation();
        if (dl == null || dl.getWorld() == null) {
            return StayResult.WRONG_WORLD;
        }
        if (!atPlayer) {
            setStay(d, new Spot(dl.getWorld().getName(), dl.getX(), dl.getY(), dl.getZ()));
            return StayResult.OK;
        }
        Location pl = by.getLocation();
        if (pl == null || pl.getWorld() == null || !pl.getWorld().getName().equals(dl.getWorld().getName())) {
            return StayResult.WRONG_WORLD;
        }
        String world = pl.getWorld().getName();
        V3 free = Formation.freeSpot(at(pl), waitingSpots(owner, world, d), cfg.formationSpacing);
        double y = stageOf(d).canFly() ? pl.getY() : groundOr(pl, free, d);
        setStay(d, new Spot(world, free.x(), y, free.z()));
        return StayResult.OK;
    }

    private double groundOr(Location pl, V3 at, EnderDragon d) {
        double gy = DragonMath.groundY(terrain(pl.getWorld()), (int) Math.floor(at.x()), (int) Math.floor(at.z()), pl.getBlockY(), 2, 6, headroom(d));
        return Double.isNaN(gy) ? pl.getY() : gy;
    }

    /**
     * Tells every dragon of the owner that can be told to wait. With {@code atPlayer} they gather around the owner in the
     * same ring they would follow in; otherwise each waits where it is. Returns how many are waiting now.
     */
    public int stayAll(Player by, boolean atPlayer) {
        List<EnderDragon> mine = new ArrayList<>();
        for (EnderDragon d : dragonsOf(by.getUniqueId())) {
            if (!rides.containsKey(d.getUniqueId())) {
                mine.add(d);
            }
        }
        if (mine.isEmpty()) {
            return 0;
        }
        if (!atPlayer || mine.size() == 1) {
            int n = 0;
            for (EnderDragon d : mine) {
                if (stay(by, d, atPlayer) == StayResult.OK) {
                    n++;
                }
            }
            return n;
        }
        Location pl = by.getLocation();
        if (pl == null || pl.getWorld() == null) {
            return 0;
        }
        List<UUID> ids = new ArrayList<>();
        for (EnderDragon d : mine) {
            ids.add(d.getUniqueId());
        }
        int n = 0;
        for (EnderDragon d : mine) {
            Location dl = d.getLocation();
            if (dl == null || dl.getWorld() == null || !dl.getWorld().getName().equals(pl.getWorld().getName())) {
                continue;
            }
            V3 off = Formation.slotFor(ids, d.getUniqueId(), cfg.formationSpacing, cfg.formationMinRadius);
            boolean fly = stageOf(d).canFly();
            double x = pl.getX() + off.x();
            double z = pl.getZ() + off.z();
            double y = fly ? pl.getY() + off.y()
                    : groundOr(pl, new V3(x, pl.getY(), z), d);
            setStay(d, new Spot(pl.getWorld().getName(), x, y, z));
            n++;
        }
        return n;
    }

    /** Tells every dragon of the owner to follow again. Returns how many had been waiting. */
    public int followAll(UUID owner) {
        int n = 0;
        for (EnderDragon d : dragonsOf(owner)) {
            if (follow(d)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ how many a player may keep

    /** How many dragons this player owns, counting ones that are asleep in unloaded chunks. */
    public int ownedCount(UUID owner) {
        return roster != null ? Math.max(roster.count(owner), dragonsOf(owner).size()) : dragonsOf(owner).size();
    }

    public int limit() {
        return cfg.maxPerPlayer;
    }

    /** Whether this player may tame one more dragon (staff with the unlimited permission always may). */
    public boolean mayTameAnother(UUID owner) {
        Player p = Bukkit.getPlayer(owner);
        if (p != null && p.hasPermission("nexushatchlings.dragons.unlimited")) {
            return true;
        }
        return ownedCount(owner) < cfg.maxPerPlayer;
    }

    /** Frees a dragon for good: it becomes a wild dragon again. Returns false when it is not this player's. */
    public boolean release(Player by, EnderDragon d) {
        UUID owner = manager.ownerOf(d);
        if (owner == null || !owner.equals(by.getUniqueId())) {
            return false;
        }
        manager.release(d);
        return true;
    }
}
