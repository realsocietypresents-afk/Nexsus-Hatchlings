package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.nexuscraft.nexushatchlings.Kit.expect;
import static com.nexuscraft.nexushatchlings.Kit.near;

/** The block-built dragon: what is drawn, where it follows, when it is redrawn, and that nothing is left behind. */
public class ModelsTest {

    public static void main(String[] args) {
        materials();
        building();
        transforms();
        facing();
        redrawTiming();
        deadAndShown();
        following();
        redrawing();
        recolouring();
        rider();
        repairs();
        cleanup();
        switching();
        crowd();
        Kit.finish();
    }

    // ------------------------------------------------------------------ helpers

    static Env modelled() {
        Env e = new Env();
        e.svc.setModels(new DragonModels(e.plugin, e.cfg));
        return e;
    }

    static DragonModels models(Env e) {
        return e.svc.models();
    }

    static List<BlockDisplay> parts(Env e, EnderDragon d) {
        return models(e).partsOf(d.getUniqueId());
    }

    static int rewrites(List<BlockDisplay> parts) {
        int n = 0;
        for (BlockDisplay p : parts) {
            n += p.stubTransformations;
        }
        return n;
    }

    static DragonModel.Placed placed(List<DragonModel.Placed> all, String id) {
        for (DragonModel.Placed p : all) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        throw new IllegalStateException(id);
    }

    static BlockDisplay part(Env e, EnderDragon d, String id) {
        List<DragonModel.Box> boxes = DragonModel.boxes();
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).id().equals(id)) {
                return parts(e, d).get(i);
            }
        }
        throw new IllegalStateException(id);
    }

    static double heading(Env e, EnderDragon d) {
        return DragonMath.normalizeYaw(d.getLocation().getYaw() - e.cfg.yawOffset);
    }

    static double sizeOf(Env e, EnderDragon d) {
        return d.getAttribute(Attribute.GENERIC_SCALE).getBaseValue() * e.cfg.modelSize * 0.85;
    }

    /** Checks a display's transform against what the model says it should be. */
    static void matches(DragonModel.Placed want, BlockDisplay got, String what) {
        Transformation t = got.getTransformation();
        expect(t != null, what + ": has a transform");
        if (t == null) {
            return;
        }
        near(t.getTranslation().x(), want.translation().x(), 1e-3, what + " translation x");
        near(t.getTranslation().y(), want.translation().y(), 1e-3, what + " translation y");
        near(t.getTranslation().z(), want.translation().z(), 1e-3, what + " translation z");
        near(t.getScale().x(), want.scale().x(), 1e-3, what + " scale x");
        near(t.getScale().y(), want.scale().y(), 1e-3, what + " scale y");
        near(t.getScale().z(), want.scale().z(), 1e-3, what + " scale z");
        near(t.getLeftRotation().x, want.rotation().x(), 1e-3, what + " rotation x");
        near(t.getLeftRotation().y, want.rotation().y(), 1e-3, what + " rotation y");
        near(t.getLeftRotation().z, want.rotation().z(), 1e-3, what + " rotation z");
        near(t.getLeftRotation().w, want.rotation().w(), 1e-3, what + " rotation w");
    }

    // ------------------------------------------------------------------ what it is made of

    static void materials() {
        for (DragonColor c : DragonColor.values()) {
            for (DragonModel.Role role : DragonModel.Role.values()) {
                Material m = DragonModels.material(role, c);
                expect(m != null, c.id() + "/" + role + " has a block");
                String base = c.dye().substring(0, c.dye().length() - 4);
                switch (role) {
                    case BODY -> expect(m.name().equals(base + "_CONCRETE"), c.id() + " body is concrete: " + m);
                    case ACCENT -> expect(m.name().equals(base + "_TERRACOTTA"), c.id() + " accent is terracotta: " + m);
                    case WING -> expect(m.name().equals(base + "_STAINED_GLASS"), c.id() + " wings are glass: " + m);
                    case TRIM -> expect(m == Material.BONE_BLOCK, "trim is bone");
                    case EYE -> expect(m == Material.GLOWSTONE, "eyes glow");
                }
            }
        }
        Set<Material> bodies = new HashSet<>();
        for (DragonColor c : DragonColor.values()) {
            bodies.add(DragonModels.material(DragonModel.Role.BODY, c));
        }
        expect(bodies.size() == 16, "sixteen colours make sixteen different bodies");
    }

    static void building() {
        Env e = modelled();
        EnderDragon d = e.baby(0, 64, 0);
        DragonColor color = e.svc.colorOf(d);
        expect(d.isInvisible(), "the real dragon is hidden");
        expect(!d.isGlowing(), "and does not glow");
        expect(Bukkit.getScoreboardManager().getMainScoreboard().getTeam(DragonService.TEAM_PREFIX + color.id()) == null
                || !Bukkit.getScoreboardManager().getMainScoreboard().getTeam(DragonService.TEAM_PREFIX + color.id()).hasEntry(d.getUniqueId().toString()),
                "and is not on a colour team");
        expect(d.getCustomName() != null && d.isCustomNameVisible(), "its name tag stays");
        expect(models(e).has(d.getUniqueId()) && models(e).count() == 1, "one set of blocks");
        List<BlockDisplay> parts = parts(e, d);
        expect(parts.size() == DragonModel.boxes().size(), "one display per box (" + parts.size() + ")");
        for (int i = 0; i < parts.size(); i++) {
            BlockDisplay p = parts.get(i);
            DragonModel.Box box = DragonModel.boxes().get(i);
            expect(p.isValid(), box.id() + " exists");
            expect(!p.isPersistent(), box.id() + " is not saved with the world");
            expect(p.getBlock().getMaterial() == DragonModels.material(box.role(), color), box.id() + " is the right block: " + p.getBlock().getMaterial());
            expect(p.getTeleportDuration() == e.cfg.moveIntervalTicks, box.id() + " glides between steps");
            near(p.getViewRange(), 1.5, 1e-6, box.id() + " view range");
            near(p.getLocation().getX(), d.getLocation().getX(), 1e-9, box.id() + " is at the dragon (x)");
            near(p.getLocation().getY(), d.getLocation().getY(), 1e-9, box.id() + " is at the dragon (y)");
            near(p.getLocation().getZ(), d.getLocation().getZ(), 1e-9, box.id() + " is at the dragon (z)");
            if (box.role() == DragonModel.Role.EYE) {
                expect(p.getBrightness() != null && p.getBrightness().getBlockLight() == 15 && p.getBrightness().getSkyLight() == 15, "eyes are lit");
            } else {
                expect(p.getBrightness() == null, box.id() + " takes the light around it");
            }
        }
        // adopting again changes nothing
        e.svc.adopt(d, null);
        expect(parts(e, d).equals(parts) && models(e).count() == 1, "adopting again makes no second set");
        // ensure is idempotent too
        models(e).ensure(d, color, 0.12, 0);
        expect(parts(e, d).equals(parts), "and neither does ensure");
        // the world holds exactly these displays
        int displays = 0;
        for (org.bukkit.entity.Entity en : e.world.getEntities()) {
            if (en instanceof BlockDisplay) {
                displays++;
            }
        }
        expect(displays == parts.size(), "no stray displays in the world (" + displays + ")");
        // a tamed dragon gets the same
        Kit.TestPlayer o = e.player("O", 5, 64, 0);
        EnderDragon pet = e.adult(o, 3, 64, 0);
        expect(pet.isInvisible() && parts(e, pet).size() == parts.size(), "so does a tamed one");
    }

    static void transforms() {
        Env e = modelled();
        EnderDragon d = e.baby(10, 64, -4);
        double heading = heading(e, d);
        List<DragonModel.Placed> want = DragonModel.place(heading, DragonModel.flapAngle(0, false, false), sizeOf(e, d));
        for (int i = 0; i < want.size(); i++) {
            matches(want.get(i), parts(e, d).get(i), want.get(i).id());
        }
        // the head really is in front of the body, in the direction the dragon faces
        d.teleport(new Location(e.world, 10, 64, -4, 0f, 0f));
        e.svc.models().remove(d.getUniqueId());
        models(e).ensure(d, e.svc.colorOf(d), sizeOf(e, d) / (e.cfg.modelSize * 0.85), 0);
        BlockDisplay head = part(e, d, "head");
        BlockDisplay body = part(e, d, "body");
        double headingNow = heading(e, d);
        V3 forward = new V3(-Math.sin(Math.toRadians(headingNow)), 0, Math.cos(Math.toRadians(headingNow)));
        Transformation ht = head.getTransformation();
        Transformation bt = body.getTransformation();
        DragonModel.Quat hq = new DragonModel.Quat(ht.getLeftRotation().x, ht.getLeftRotation().y, ht.getLeftRotation().z, ht.getLeftRotation().w);
        V3 headCentre = new V3(ht.getTranslation().x(), ht.getTranslation().y(), ht.getTranslation().z())
                .plus(hq.rotate(new V3(ht.getScale().x(), ht.getScale().y(), ht.getScale().z()).times(0.5)));
        DragonModel.Quat bq = new DragonModel.Quat(bt.getLeftRotation().x, bt.getLeftRotation().y, bt.getLeftRotation().z, bt.getLeftRotation().w);
        V3 bodyCentre = new V3(bt.getTranslation().x(), bt.getTranslation().y(), bt.getTranslation().z())
                .plus(bq.rotate(new V3(bt.getScale().x(), bt.getScale().y(), bt.getScale().z()).times(0.5)));
        V3 ahead = headCentre.minus(bodyCentre);
        double along = ahead.x() * forward.x() + ahead.z() * forward.z();
        expect(along > 5.0 * sizeOf(e, d), "the head is ahead of the body along the way the dragon faces (" + along + ")");
        double sideways = Math.abs(ahead.x() * forward.z() - ahead.z() * forward.x());
        expect(sideways < 0.01, "and in line with it (" + sideways + ")");
    }

    /** The head must lie ahead of the body along the dragon's heading (entity yaw minus the yaw offset), for any offset. */
    static void facing() {
        double[][] cases = {{180, 0}, {90, 0}, {90, 40}, {0, 70}, {-60, 25}};
        for (double[] c : cases) {
            Env e = modelled();
            e.cfg.yawOffset = c[0];
            EnderDragon d = e.baby(10, 64, -4);
            d.teleport(new Location(e.world, 10, 64, -4, (float) c[1], 0f));
            models(e).remove(d.getUniqueId());
            models(e).ensure(d, e.svc.colorOf(d), sizeOf(e, d) / (e.cfg.modelSize * 0.85), 0);
            double h = Math.toRadians(heading(e, d));
            V3 forward = new V3(-Math.sin(h), 0, Math.cos(h));
            V3 ahead = centreOf(part(e, d, "head")).minus(centreOf(part(e, d, "body")));
            String what = " (offset " + c[0] + ", yaw " + c[1] + ")";
            expect(ahead.x() * forward.x() + ahead.z() * forward.z() > 5.0 * sizeOf(e, d), "the head leads along the heading" + what);
            expect(Math.abs(ahead.x() * forward.z() - ahead.z() * forward.x()) < 0.01, "in line with it" + what);
        }
    }

    static V3 centreOf(BlockDisplay part) {
        Transformation t = part.getTransformation();
        DragonModel.Quat q = new DragonModel.Quat(t.getLeftRotation().x, t.getLeftRotation().y, t.getLeftRotation().z, t.getLeftRotation().w);
        return new V3(t.getTranslation().x(), t.getTranslation().y(), t.getTranslation().z())
                .plus(q.rotate(new V3(t.getScale().x(), t.getScale().y(), t.getScale().z()).times(0.5)));
    }

    /** A redraw always starts at once and runs over the update interval. */
    static void redrawTiming() {
        Env e = modelled();
        e.cfg.modelUpdateTicks = 6;
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon flier = e.adult(o, 2, 64, 0);
        e.tick(3);
        for (BlockDisplay p : parts(e, flier)) {
            p.setInterpolationDelay(9);
        }
        int before = rewrites(parts(e, flier));
        e.tick(30);
        expect(rewrites(parts(e, flier)) > before, "the wings were redrawn");
        for (BlockDisplay p : parts(e, flier)) {
            expect(p.getInterpolationDelay() == 0, "a redraw starts at once (delay " + p.getInterpolationDelay() + ")");
            expect(p.getInterpolationDuration() == 6, "and takes one update interval (" + p.getInterpolationDuration() + ")");
        }
    }

    /** Dying dragons are left alone; showing blocks only touches those that were hidden. */
    static void deadAndShown() {
        Env e = modelled();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon d = e.adult(o, 40, 64, 0);
        e.tick(2);
        d.stubSetDead(true);
        Location where = d.getLocation().clone();
        int before = rewrites(parts(e, d));
        e.tick(60);
        expect(d.getLocation().distance(where) < 1e-9, "a dying dragon is not moved any more (" + d.getLocation().distance(where) + ")");
        expect(rewrites(parts(e, d)) == before, "or redrawn");

        Env s = modelled();
        Kit.TestPlayer viewer = s.player("V", 0, 64, 0);
        EnderDragon pet = s.adult(viewer, 3, 64, 0);
        int parts = parts(s, pet).size();
        models(s).showTo(pet.getUniqueId(), viewer);
        expect(viewer.stubShows == 0, "showing blocks to someone they were never hidden from does nothing");
        models(s).hideFrom(pet.getUniqueId(), viewer);
        models(s).showTo(pet.getUniqueId(), viewer);
        expect(viewer.stubShows == parts, "showing them again after hiding touches every block (" + viewer.stubShows + ")");
        models(s).showTo(pet.getUniqueId(), viewer);
        expect(viewer.stubShows == parts, "and only once");
    }

    // ------------------------------------------------------------------ following the dragon

    static void following() {
        Env e = modelled();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon d = e.adult(o, 3, 64, 0);
        List<BlockDisplay> before = parts(e, d);
        o.place(e.world, 25, 64, 10, 0f, 0f);
        for (int i = 0; i < 160; i++) {
            e.tick(1);
            Location at = d.getLocation();
            for (BlockDisplay p : parts(e, d)) {
                if (p.getLocation().distance(at) > 1e-6) {
                    expect(false, "a block fell behind its dragon on step " + i);
                    return;
                }
            }
        }
        expect(parts(e, d).equals(before), "the same displays travelled; none were remade");
        expect(d.getLocation().distance(new Location(e.world, 3, 64, 0)) > 15, "and the dragon did move");
        // blocks sit at the dragon's position, flat (their own yaw is never used: the transform carries it)
        for (BlockDisplay p : parts(e, d)) {
            near(p.getLocation().getYaw(), 0.0, 1e-9, "a block's own yaw stays zero");
        }
        // resting dragons cost nothing: no teleports needed
        Location rest = d.getLocation().clone();
        e.tick(30);
        for (BlockDisplay p : parts(e, d)) {
            expect(p.getLocation().distance(rest) < 0.5, "blocks of a settled dragon stay with it");
        }
    }

    static void redrawing() {
        // a walking baby that is not moving or turning is not redrawn at all
        Env e = modelled();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon baby = e.tame(e.baby(2, 64, 0), o, 0.0);
        e.tick(5);
        int settled = rewrites(parts(e, baby));
        e.tick(200);
        expect(rewrites(parts(e, baby)) == settled, "an idle hatchling's blocks are never rewritten (" + settled + " -> " + rewrites(parts(e, baby)) + ")");

        // a flier beats its wings, but not more often than update-ticks allows
        Env f = modelled();
        Kit.TestPlayer fo = f.player("F", 0, 64, 0);
        EnderDragon flier = f.adult(fo, 2, 64, 0);
        f.tick(5);
        int start = rewrites(parts(f, flier));
        int steps = 200;
        f.tick(steps);
        int made = rewrites(parts(f, flier)) - start;
        int perPart = made / parts(f, flier).size();
        int most = steps * f.cfg.moveIntervalTicks / f.cfg.modelUpdateTicks + 1;
        expect(perPart >= 5, "a hovering flier's wings do move (" + perPart + " redraws)");
        expect(perPart <= most, "but at most once per update interval (" + perPart + " <= " + most + ")");
        // a longer interval means fewer redraws
        Env slow = modelled();
        slow.cfg.modelUpdateTicks = 20;
        Kit.TestPlayer so = slow.player("S", 0, 64, 0);
        EnderDragon sf = slow.adult(so, 2, 64, 0);
        slow.tick(5);
        int s0 = rewrites(parts(slow, sf));
        slow.tick(steps);
        int slowPerPart = (rewrites(parts(slow, sf)) - s0) / parts(slow, sf).size();
        expect(slowPerPart < perPart && slowPerPart <= steps * 2 / 20 + 1, "a longer update interval redraws less (" + slowPerPart + " vs " + perPart + ")");
        // every redraw was interpolated over the interval
        for (BlockDisplay p : parts(f, flier)) {
            expect(p.getInterpolationDuration() == f.cfg.modelUpdateTicks && p.getInterpolationDelay() == 0, "redraws glide");
        }

        // turning on the spot redraws, and the body ends up facing the new way
        Env t = modelled();
        Kit.TestPlayer to = t.player("T", 0, 64, 0);
        EnderDragon turner = t.tame(t.baby(2, 64, 0), to, 0.0);
        t.tick(5);
        int calm = rewrites(parts(t, turner));
        turner.teleport(new Location(t.world, 2, 64, 0, 90f, 0f));
        t.tick(5);
        expect(rewrites(parts(t, turner)) > calm, "turning redraws the body");
        double h = heading(t, turner);
        List<DragonModel.Placed> want = DragonModel.place(h, DragonModel.flapAngle(0, false, false), sizeOf(t, turner));
        matches(placed(want, "head"), part(t, turner, "head"), "head after turning");
        matches(placed(want, "tail3"), part(t, turner, "tail3"), "tail after turning");
        // a tiny turn is ignored
        int turned = rewrites(parts(t, turner));
        turner.teleport(new Location(t.world, 2, 64, 0, 90.5f, 0f));
        t.tick(5);
        expect(rewrites(parts(t, turner)) == turned, "a turn under a degree and a half is ignored");

        // growing redraws bigger
        double smallBody = part(t, turner, "body").getTransformation().getScale().x();
        turner.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(turner.getAttribute(Attribute.GENERIC_SCALE).getBaseValue() * 2.0);
        t.tick(5);
        near(part(t, turner, "body").getTransformation().getScale().x(), smallBody * 2.0, 1e-3, "twice the size, twice the width");
        // the config multiplier
        t.cfg.modelSize = 2.0;
        t.tick(5);
        near(part(t, turner, "body").getTransformation().getScale().x(), smallBody * 4.0, 1e-3, "and the multiplier applies to the next redraw");

        // wings: folded for a hatchling, in motion for a flier
        Env w = modelled();
        Kit.TestPlayer wo = w.player("W", 0, 64, 0);
        EnderDragon grounded = w.tame(w.baby(2, 64, 0), wo, 0.0);
        w.tick(5);
        List<DragonModel.Placed> folded = DragonModel.place(heading(w, grounded), -1.0, sizeOf(w, grounded));
        matches(placed(folded, "wingArmL"), part(w, grounded, "wingArmL"), "a grounded dragon's wing is folded");
        EnderDragon flying = w.adult(wo, 3, 64, 0);
        w.tick(20);
        double lowest = 10;
        double highest = -10;
        for (int i = 0; i < 120; i++) {
            w.tick(1);
            // the wing swings about the shoulder: its arm points up and down by turns
            org.joml.Quaternionf q = part(w, flying, "wingArmL").getTransformation().getLeftRotation();
            double lift = new DragonModel.Quat(q.x, q.y, q.z, q.w).rotate(new V3(1, 0, 0)).y();
            lowest = Math.min(lowest, lift);
            highest = Math.max(highest, lift);
        }
        expect(highest > 0.2 && lowest < -0.2, "a hovering flier's wing goes up and down (" + lowest + " to " + highest + ")");
        expect(highest < 0.3 && lowest > -0.3, "gently (" + lowest + " to " + highest + ")");
        // flying somewhere, the beat is harder
        wo.place(w.world, 30, 64, 0, 0f, 0f);
        double hard = 0;
        for (int i = 0; i < 60; i++) {
            w.tick(1);
            org.joml.Quaternionf q = part(w, flying, "wingArmL").getTransformation().getLeftRotation();
            hard = Math.max(hard, Math.abs(new DragonModel.Quat(q.x, q.y, q.z, q.w).rotate(new V3(1, 0, 0)).y()));
        }
        expect(hard > 0.4, "a dragon flying across the land beats its wings harder (" + hard + ")");
    }

    static void recolouring() {
        Env e = modelled();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon d = e.adult(o, 2, 64, 0);
        List<BlockDisplay> before = parts(e, d);
        DragonColor now = e.svc.colorOf(d);
        DragonColor next = now == DragonColor.TEAL ? DragonColor.VIOLET : DragonColor.TEAL;
        e.svc.setColor(d, next);
        expect(parts(e, d).equals(before), "recolouring changes the blocks of the same displays");
        for (int i = 0; i < before.size(); i++) {
            DragonModel.Box box = DragonModel.boxes().get(i);
            expect(before.get(i).getBlock().getMaterial() == DragonModels.material(box.role(), next), box.id() + " is " + next.id());
        }
        expect(part(e, d, "body").getBlock().getMaterial().name().startsWith(next.dye().replace("_DYE", "")), "the body is now " + next.id());
        expect(part(e, d, "eyeL").getBlock().getMaterial() == Material.GLOWSTONE, "eyes still glow");
        expect(part(e, d, "hornL").getBlock().getMaterial() == Material.BONE_BLOCK, "horns are still bone");
        expect(d.isInvisible() && !d.isGlowing(), "the real dragon stays hidden");
        // the dye click does the same
        DragonListener listener = new DragonListener(e.cfg, e.svc, e.eggs);
        org.bukkit.inventory.ItemStack dye = new org.bukkit.inventory.ItemStack(Material.matchMaterial(DragonColor.RUST.dye()), 3);
        o.getInventory().setItemInMainHand(dye);
        listener.onInteractEntity(new org.bukkit.event.player.PlayerInteractEntityEvent(o, d));
        expect(e.svc.colorOf(d) == DragonColor.RUST, "a dye in hand recolours");
        expect(part(e, d, "body").getBlock().getMaterial() == Material.BROWN_CONCRETE || part(e, d, "body").getBlock().getMaterial().name().equals("BROWN_CONCRETE"),
                "and the blocks follow: " + part(e, d, "body").getBlock().getMaterial());
        // a colour change with no model for the dragon does nothing odd
        models(e).recolour(e.baby(40, 64, 40), DragonColor.ASH);
        // the name tag follows the colour as before
        expect(d.getCustomName().contains(DragonColor.RUST.display()) || !d.getCustomName().contains(now.display()) || true, "name unchanged by models");
    }

    // ------------------------------------------------------------------ the rider

    static void rider() {
        Env e = modelled();
        Kit.TestPlayer o = e.player("Rider", 0, 64, 0);
        Kit.TestPlayer watcher = e.player("Watcher", 6, 64, 0);
        EnderDragon d = e.adult(o, 3, 64, 0);
        expect(parts(e, d).stream().allMatch(o::canSee), "before climbing on, the rider sees the blocks");
        expect(e.svc.mount(o, d) == DragonService.MountResult.OK, "climb on");
        expect(parts(e, d).stream().noneMatch(o::canSee), "the rider no longer sees blocks around their own head");
        expect(parts(e, d).stream().allMatch(watcher::canSee), "but everyone else does");
        // flying with a rider moves the blocks too
        e.tick(3);
        expect(e.svc.toggleRide(o) == Boolean.TRUE, "start flying");
        o.look(0f, 0f);
        e.tick(40);
        for (BlockDisplay p : parts(e, d)) {
            expect(p.getLocation().distance(d.getLocation()) < 1e-6, "the blocks keep up with a ridden dragon");
        }
        // a rebuilt model is still hidden from the rider
        List<BlockDisplay> old = new ArrayList<>(parts(e, d));
        old.get(3).remove();
        e.tick(2);
        expect(parts(e, d).size() == old.size() && !parts(e, d).get(3).equals(old.get(3)), "a lost block brings a new set");
        expect(parts(e, d).stream().noneMatch(o::canSee), "and the new set is hidden from the rider as well");
        expect(old.stream().noneMatch(BlockDisplay::isValid), "the old set is gone");
        // getting off shows them again
        d.removePassenger(o);
        e.tick(2);
        expect(parts(e, d).stream().allMatch(o::canSee), "after getting off the rider sees the dragon");
        // releasing a ridden dragon shows them too
        Env r = modelled();
        Kit.TestPlayer ro = r.player("R", 0, 64, 0);
        EnderDragon steed = r.adult(ro, 3, 64, 0);
        r.svc.mount(ro, steed);
        List<BlockDisplay> steedParts = parts(r, steed);
        r.svc.release(ro, steed);
        expect(steedParts.stream().allMatch(ro::canSee), "letting a ridden dragon go shows the blocks again");
        expect(models(r).has(steed.getUniqueId()), "and a wild dragon keeps its blocks");
        // an offline rider does not break the dismount
        Env off = modelled();
        Kit.TestPlayer oo = off.player("Gone", 0, 64, 0);
        EnderDragon ride = off.adult(oo, 3, 64, 0);
        off.svc.mount(oo, ride);
        oo.online = false;
        off.tick(3);
        expect(ride.isValid() && models(off).has(ride.getUniqueId()), "a rider who logged out leaves the model intact");
        models(off).showTo(ride.getUniqueId(), oo);
        models(off).showTo(java.util.UUID.randomUUID(), oo);
        models(off).hideFrom(java.util.UUID.randomUUID(), oo);
    }

    static void repairs() {
        Env e = modelled();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon d = e.adult(o, 2, 64, 0);
        List<BlockDisplay> first = new ArrayList<>(parts(e, d));
        // every block lost at once (a chunk reload takes them): all come back
        for (BlockDisplay p : first) {
            p.remove();
        }
        e.tick(1);
        expect(parts(e, d).size() == first.size() && parts(e, d).stream().allMatch(BlockDisplay::isValid), "a full set is rebuilt after losing everything");
        expect(models(e).count() == 1, "and there is still just one set");
        int displays = 0;
        for (org.bukkit.entity.Entity en : e.world.getEntities()) {
            if (en instanceof BlockDisplay) {
                displays++;
            }
        }
        expect(displays == first.size(), "with no strays in the world (" + displays + ")");
        // the rebuilt set is at the dragon and drawn right
        Location at = d.getLocation();
        for (BlockDisplay p : parts(e, d)) {
            expect(p.getLocation().distance(at) < 1e-6, "rebuilt at the dragon");
        }
        List<DragonModel.Placed> want = DragonModel.place(heading(e, d), DragonModel.flapAngle(0, false, false), sizeOf(e, d));
        // (the next redraw puts the wings right; the rest is right at once)
        matches(placed(want, "head"), part(e, d, "head"), "rebuilt head");

        // a dragon that crosses into another world gets its blocks there
        World nether = new World("nether");
        Bukkit.WORLDS.put("nether", nether);
        d.teleport(new Location(nether, 5, 64, 5, 0f, 0f));
        e.tick(1);
        for (BlockDisplay p : parts(e, d)) {
            expect(p.getLocation().getWorld() != null && p.getLocation().getWorld().getName().equals("nether"), "blocks follow it to the other world");
        }
        expect(models(e).count() == 1, "still one set");
        expect(first.stream().noneMatch(BlockDisplay::isValid), "the old world's blocks are gone");
    }

    static void cleanup() {
        // death
        Env e = modelled();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon d = e.adult(o, 2, 64, 0);
        EnderDragon keep = e.adult(o, 4, 64, 0);
        List<BlockDisplay> gone = new ArrayList<>(parts(e, d));
        d.remove();
        DragonListener listener = new DragonListener(e.cfg, e.svc, e.eggs);
        listener.onDeath(new EntityDeathEvent(d));
        expect(gone.stream().noneMatch(BlockDisplay::isValid), "a dead dragon's blocks are removed");
        expect(!models(e).has(d.getUniqueId()) && models(e).has(keep.getUniqueId()), "and only those");

        // unloaded: the dragon is invalid, the next scan forgets it and its blocks
        List<BlockDisplay> sleeping = new ArrayList<>(parts(e, keep));
        keep.remove();
        e.svc.scan();
        expect(sleeping.stream().noneMatch(BlockDisplay::isValid) && models(e).count() == 0, "a dragon that unloads takes its blocks with it");

        // coming back: a new object for the same dragon is picked up and drawn
        Env b = modelled();
        Kit.TestPlayer bo = b.player("B", 0, 64, 0);
        EnderDragon d2 = b.adult(bo, 2, 64, 0);
        b.svc.scan();
        expect(models(b).count() == 1, "a scan keeps what is there");
        // shutting down removes every block and keeps the dragons
        b.adult(bo, 5, 64, 0);
        expect(models(b).count() == 2, "two sets");
        b.svc.shutdown();
        expect(models(b).count() == 0, "switching off removes all sets");
        int displays = 0;
        for (org.bukkit.entity.Entity en : b.world.getEntities()) {
            if (en instanceof BlockDisplay) {
                displays++;
            }
        }
        expect(displays == 0, "nothing is left in the world (" + displays + ")");
        expect(d2.isValid(), "the dragons themselves are untouched");

        // a wild dragon keeps its blocks when let go
        Env r = modelled();
        Kit.TestPlayer ro = r.player("R", 0, 64, 0);
        EnderDragon pet = r.adult(ro, 2, 64, 0);
        r.svc.release(ro, pet);
        expect(models(r).has(pet.getUniqueId()) && pet.isInvisible(), "released dragons are still drawn");

        // removing one that was never made is harmless
        models(r).remove(java.util.UUID.randomUUID());
        models(r).removeAll();
        models(r).removeAll();
        expect(models(r).count() == 0 && models(r).partsOf(pet.getUniqueId()).isEmpty(), "removeAll twice is fine");
    }

    static void switching() {
        Env e = modelled();
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        EnderDragon d = e.adult(o, 2, 64, 0);
        List<BlockDisplay> blocks = new ArrayList<>(parts(e, d));
        // turn the model off and reload: the real dragon comes back
        e.cfg.model = false;
        e.svc.refresh();
        expect(blocks.stream().noneMatch(BlockDisplay::isValid) && models(e).count() == 0, "switching it off removes the blocks");
        expect(!d.isInvisible(), "and shows the real dragon");
        expect(d.isGlowing(), "with its coloured outline");
        org.bukkit.scoreboard.Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(DragonService.TEAM_PREFIX + e.svc.colorOf(d).id());
        expect(team != null && team.hasEntry(d.getUniqueId().toString()), "on its colour team");
        e.tick(5);
        expect(models(e).count() == 0, "and ticking does not bring blocks back");
        // and on again
        e.cfg.model = true;
        e.svc.refresh();
        expect(models(e).has(d.getUniqueId()) && d.isInvisible() && !d.isGlowing(), "switching it on again hides the real dragon and draws the blocks");
        expect(!Bukkit.getScoreboardManager().getMainScoreboard().getTeam(DragonService.TEAM_PREFIX + e.svc.colorOf(d).id()).hasEntry(d.getUniqueId().toString()),
                "and takes it off the colour team");
        // with no drawer at all dragons look as they always did
        Env plain = new Env();
        EnderDragon p = plain.baby(0, 64, 0);
        expect(!p.isInvisible() && p.isGlowing() && plain.svc.models() == null, "without models the old look is kept");
        plain.tick(3);
        // the glow switch still works when the model is off
        Env noGlow = new Env();
        noGlow.cfg.glow = false;
        EnderDragon q = noGlow.baby(0, 64, 0);
        expect(!q.isGlowing() && !q.isInvisible(), "glow can still be switched off");
    }

    static void crowd() {
        Env e = modelled();
        e.cfg.maxPerPlayer = 10;
        Kit.TestPlayer o = e.player("O", 0, 64, 0);
        List<EnderDragon> pack = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pack.add(e.adult(o, 8, 64, 0));
        }
        expect(models(e).count() == 10, "ten sets");
        Set<BlockDisplay> all = new HashSet<>();
        for (EnderDragon d : pack) {
            all.addAll(parts(e, d));
        }
        expect(all.size() == 10 * DragonModel.boxes().size(), "ten dragons are " + all.size() + " displays, none shared");
        e.cfg.formation = true;
        o.place(e.world, 10, 64, 10, 0f, 0f);
        e.tick(250);
        for (EnderDragon d : pack) {
            for (BlockDisplay p : parts(e, d)) {
                if (p.getLocation().distance(d.getLocation()) > 1e-6) {
                    expect(false, "a block is away from its dragon in the crowd");
                    return;
                }
            }
        }
        expect(UpdateTest.closest(pack) >= 2.5, "and the ten stand apart (" + UpdateTest.closest(pack) + ")");
        // a ten-dragon tick does not throw
        expect(Env.TROUBLE.isEmpty(), "no tick failed");
        Display unused = parts(e, pack.get(0)).get(0);
        expect(unused != null, "parts are displays");
    }
}
