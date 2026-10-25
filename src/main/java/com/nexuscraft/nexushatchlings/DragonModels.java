package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws each dragon as a set of coloured block displays (the shape comes from {@link DragonModel}) while the real, hidden
 * Ender Dragon keeps the hitbox, the movement and everything else. The displays are not saved with the world: they are made again
 * whenever the dragon is picked up after a restart or a chunk load, and removed with it. All the displays of a dragon sit at its
 * position; where each box really is comes from its transform, which is only rewritten when the dragon turns or its wings move.
 *
 * <p>Java players see the blocks. Bedrock players (through Geyser) do not get display entities, so they see only the name tag and
 * the coloured dust.
 */
public final class DragonModels {

    private static final class Rig {
        final List<BlockDisplay> parts = new ArrayList<>();
        final List<DragonModel.Role> roles = new ArrayList<>();
        final Set<UUID> hiddenFrom = new HashSet<>();
        DragonColor color;
        Location at;
        double heading = Double.NaN;
        double flap = Double.NaN;
        double size = Double.NaN;
        long lastTransform = Long.MIN_VALUE;
        long lastMoved = Long.MIN_VALUE;
    }

    /** How long after the last step a dragon still counts as flying somewhere (so the wings do not stutter between steps). */
    private static final long MOVING_GRACE_TICKS = 10;

    private final JavaPlugin plugin;
    private final DragonConfig cfg;
    private final Map<UUID, Rig> rigs = new HashMap<>();

    public DragonModels(JavaPlugin plugin, DragonConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public boolean has(UUID dragon) {
        return rigs.containsKey(dragon);
    }

    public int count() {
        return rigs.size();
    }

    /** The displays of one dragon (empty if it has none). */
    public List<BlockDisplay> partsOf(UUID dragon) {
        Rig rig = rigs.get(dragon);
        return rig == null ? List.of() : List.copyOf(rig.parts);
    }

    // ------------------------------------------------------------------ materials

    /** The block that draws a role in a colour. */
    static Material material(DragonModel.Role role, DragonColor color) {
        String base = color.dye().substring(0, color.dye().length() - "_DYE".length());
        return switch (role) {
            case BODY -> Material.valueOf(base + "_CONCRETE");
            case ACCENT -> Material.valueOf(base + "_TERRACOTTA");
            case WING -> Material.valueOf(base + "_STAINED_GLASS");
            case TRIM -> Material.BONE_BLOCK;
            case EYE -> Material.GLOWSTONE;
        };
    }

    // ------------------------------------------------------------------ making and removing

    /** Makes sure this dragon has all its displays, in this colour, at its position. Safe to call again. */
    public void ensure(EnderDragon dragon, DragonColor color, double scale, long ticks) {
        UUID id = dragon.getUniqueId();
        Location at = dragon.getLocation();
        if (at == null || at.getWorld() == null) {
            return;
        }
        Rig rig = rigs.get(id);
        if (rig != null && (!intact(rig) || !sameWorld(rig.at, at))) {
            discard(rig);
            rigs.remove(id);
            Set<UUID> keep = new HashSet<>(rig.hiddenFrom);
            build(dragon, color, scale, ticks, keep);
            return;
        }
        if (rig == null) {
            build(dragon, color, scale, ticks, Set.of());
        } else if (rig.color != color) {
            recolour(dragon, color);
        }
    }

    private static boolean sameWorld(Location a, Location b) {
        return a != null && a.getWorld() != null && b.getWorld() != null && a.getWorld().getName().equals(b.getWorld().getName());
    }

    private static boolean intact(Rig rig) {
        for (BlockDisplay part : rig.parts) {
            if (!part.isValid()) {
                return false;
            }
        }
        return !rig.parts.isEmpty();
    }

    private void build(EnderDragon dragon, DragonColor color, double scale, long ticks, Set<UUID> hideFrom) {
        Location at = dragon.getLocation();
        World world = at.getWorld();
        double heading = headingOf(at);
        double size = sizeOf(scale);
        double flap = DragonModel.flapAngle(ticks, false, false);
        List<DragonModel.Placed> placed = DragonModel.place(heading, flap, size);
        Rig rig = new Rig();
        rig.color = color;
        rig.at = at.clone();
        rig.heading = heading;
        rig.flap = flap;
        rig.size = size;
        rig.lastTransform = ticks;
        Location spot = flat(at);
        for (DragonModel.Placed p : placed) {
            DragonModel.Role role = p.role();
            BlockDisplay display = world.spawn(spot, BlockDisplay.class, d -> {
                d.setBlock(Bukkit.createBlockData(material(role, color)));
                d.setTransformation(transformOf(p));
                d.setInterpolationDuration(0);
                d.setTeleportDuration(Math.max(1, Math.min(59, cfg.moveIntervalTicks)));
                d.setViewRange((float) cfg.modelViewRange);
                d.setPersistent(false);
                if (role == DragonModel.Role.EYE) {
                    d.setBrightness(new Display.Brightness(15, 15));
                }
            });
            rig.parts.add(display);
            rig.roles.add(role);
        }
        rigs.put(dragon.getUniqueId(), rig);
        for (UUID viewer : hideFrom) {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null) {
                hide(rig, p);
            }
        }
    }

    private void discard(Rig rig) {
        for (BlockDisplay part : rig.parts) {
            part.remove();
        }
    }

    /** Takes a dragon's displays away. */
    public void remove(UUID dragon) {
        Rig rig = rigs.remove(dragon);
        if (rig != null) {
            discard(rig);
        }
    }

    public void removeAll() {
        for (Rig rig : rigs.values()) {
            discard(rig);
        }
        rigs.clear();
    }

    // ------------------------------------------------------------------ colour

    /** Changes the blocks of every box to the new colour. */
    public void recolour(EnderDragon dragon, DragonColor color) {
        Rig rig = rigs.get(dragon.getUniqueId());
        if (rig == null) {
            return;
        }
        rig.color = color;
        for (int i = 0; i < rig.parts.size(); i++) {
            rig.parts.get(i).setBlock(Bukkit.createBlockData(material(rig.roles.get(i), color)));
        }
    }

    // ------------------------------------------------------------------ movement and wings

    private static Location flat(Location at) {
        Location l = at.clone();
        l.setYaw(0f);
        l.setPitch(0f);
        return l;
    }

    private double headingOf(Location at) {
        return DragonMath.normalizeYaw(at.getYaw() - cfg.yawOffset);
    }

    private double sizeOf(double scale) {
        return Math.max(0.02, scale * cfg.modelSize * 0.85);
    }

    private static Transformation transformOf(DragonModel.Placed p) {
        return new Transformation(
                new Vector3f((float) p.translation().x(), (float) p.translation().y(), (float) p.translation().z()),
                new Quaternionf((float) p.rotation().x(), (float) p.rotation().y(), (float) p.rotation().z(), (float) p.rotation().w()),
                new Vector3f((float) p.scale().x(), (float) p.scale().y(), (float) p.scale().z()),
                new Quaternionf());
    }

    /**
     * Keeps the displays on their dragon: moves them when it moved, and redraws the shape when it turned or its wings should be
     * somewhere else (at most once every {@code update-ticks}).
     */
    public void update(EnderDragon dragon, DragonColor color, double scale, boolean canFly, boolean flying, long ticks) {
        UUID id = dragon.getUniqueId();
        Location at = dragon.getLocation();
        if (at == null || at.getWorld() == null) {
            return;
        }
        Rig rig = rigs.get(id);
        if (rig == null || !intact(rig) || !sameWorld(rig.at, at)) {
            ensure(dragon, color, scale, ticks);
            return;
        }
        if (rig.color != color) {
            recolour(dragon, color);
        }
        boolean moved = rig.at.distanceSquared(at) > 0.0004;
        if (moved) {
            rig.lastMoved = ticks;
            Location spot = flat(at);
            for (BlockDisplay part : rig.parts) {
                part.teleport(spot);
            }
            rig.at = at.clone();
        }
        boolean moving = flying || (rig.lastMoved != Long.MIN_VALUE && ticks - rig.lastMoved < MOVING_GRACE_TICKS);
        double heading = headingOf(at);
        double flap = DragonModel.flapAngle(ticks, canFly, moving);
        double size = sizeOf(scale);
        boolean turned = Math.abs(DragonMath.normalizeYaw(heading - rig.heading)) > 1.5 || Double.isNaN(rig.heading);
        boolean waved = Math.abs(flap - rig.flap) > 0.04 || Double.isNaN(rig.flap);
        boolean grown = Math.abs(size - rig.size) > rig.size * 0.01 || Double.isNaN(rig.size);
        if ((turned || waved || grown) && ticks - rig.lastTransform >= cfg.modelUpdateTicks) {
            List<DragonModel.Placed> placed = DragonModel.place(heading, flap, size);
            int duration = Math.max(1, cfg.modelUpdateTicks);
            for (int i = 0; i < rig.parts.size() && i < placed.size(); i++) {
                BlockDisplay part = rig.parts.get(i);
                part.setTransformation(transformOf(placed.get(i)));
                part.setInterpolationDelay(0);
                part.setInterpolationDuration(duration);
            }
            rig.heading = heading;
            rig.flap = flap;
            rig.size = size;
            rig.lastTransform = ticks;
        }
    }

    // ------------------------------------------------------------------ the rider's own view

    /** Hides the dragon's blocks from one player (a rider would otherwise be sitting inside them). */
    public void hideFrom(UUID dragon, Player viewer) {
        Rig rig = rigs.get(dragon);
        if (rig != null) {
            hide(rig, viewer);
        }
    }

    private void hide(Rig rig, Player viewer) {
        rig.hiddenFrom.add(viewer.getUniqueId());
        for (BlockDisplay part : rig.parts) {
            viewer.hideEntity(plugin, part);
        }
    }

    /** Shows the dragon's blocks to a player again. */
    public void showTo(UUID dragon, Player viewer) {
        Rig rig = rigs.get(dragon);
        if (rig == null || !rig.hiddenFrom.remove(viewer.getUniqueId())) {
            return;
        }
        if (!viewer.isOnline()) {
            return;
        }
        for (BlockDisplay part : rig.parts) {
            viewer.showEntity(plugin, part);
        }
    }
}
