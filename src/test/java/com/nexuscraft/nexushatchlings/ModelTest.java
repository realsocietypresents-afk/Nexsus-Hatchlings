package com.nexuscraft.nexushatchlings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexushatchlings.Kit.expect;
import static com.nexuscraft.nexushatchlings.Kit.near;

/** The two pure pieces behind the new look and the new spacing: the ring that keeps dragons apart, and the block-built dragon shape. */
public class ModelTest {

    public static void main(String[] args) {
        quaternions();
        ringSlots();
        freeSpots();
        slotAssignment();
        shape();
        placement();
        flapping();
        Kit.finish();
    }

    // ------------------------------------------------------------------ rotations

    static void quaternions() {
        DragonModel.Quat yaw90 = DragonModel.Quat.axis(0, 1, 0, Math.PI / 2);
        V3 z = yaw90.rotate(new V3(0, 0, 1));
        near(z.x(), 1.0, 1e-9, "a quarter turn about Y takes +Z to +X (x)");
        near(z.z(), 0.0, 1e-9, "a quarter turn about Y takes +Z to +X (z)");
        V3 up = DragonModel.Quat.axis(1, 0, 0, Math.PI / 2).rotate(new V3(0, 1, 0));
        near(up.z(), 1.0, 1e-9, "a quarter turn about X takes +Y to +Z");
        V3 same = DragonModel.Quat.IDENTITY.rotate(new V3(3, -2, 5));
        near(same.x(), 3.0, 1e-12, "identity keeps x");
        near(same.y(), -2.0, 1e-12, "identity keeps y");
        near(same.z(), 5.0, 1e-12, "identity keeps z");
        // composing two turns equals turning twice
        DragonModel.Quat a = DragonModel.Quat.axis(0, 1, 0, 0.7);
        DragonModel.Quat b = DragonModel.Quat.axis(1, 0, 0, -0.4);
        V3 v = new V3(1.5, 2.0, -0.5);
        V3 twice = a.rotate(b.rotate(v));
        V3 once = a.times(b).rotate(v);
        near(once.x(), twice.x(), 1e-9, "a*b applies b first (x)");
        near(once.y(), twice.y(), 1e-9, "a*b applies b first (y)");
        near(once.z(), twice.z(), 1e-9, "a*b applies b first (z)");
        near(once.length(), v.length(), 1e-9, "turning keeps length");
        DragonModel.Quat q = a.times(b);
        near(Math.sqrt(q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w()), 1.0, 1e-9, "a product of turns stays a unit quaternion");
    }

    // ------------------------------------------------------------------ the ring

    static void ringSlots() {
        expect(Formation.offsets(0, 6, 4).isEmpty(), "no dragons, no slots");
        expect(Formation.offsets(-3, 6, 4).isEmpty(), "negative count, no slots");
        List<V3> one = Formation.offsets(1, 6, 4);
        expect(one.size() == 1 && one.get(0).x() == 0 && one.get(0).z() == 0 && one.get(0).y() == 0, "a single dragon comes straight to its owner");
        for (int n = 2; n <= 10; n++) {
            List<V3> slots = Formation.offsets(n, 6.0, 4.0);
            expect(slots.size() == n, n + " slots for " + n + " dragons");
            expect(Formation.closest(slots) >= 6.0 - 1e-9, n + " dragons stand at least 6 blocks apart (closest " + Formation.closest(slots) + ")");
            double r0 = Math.hypot(slots.get(0).x(), slots.get(0).z());
            for (V3 s : slots) {
                near(Math.hypot(s.x(), s.z()), r0, 1e-9, "every slot is on the same ring (" + n + ")");
                expect(s.y() >= 0 && s.y() <= 2 * Formation.LIFT_STEP + 1e-9, "lift stays small");
            }
            expect(r0 >= 4.0 - 1e-9, "the ring is never tighter than the minimum (" + n + ")");
        }
        // fliers are lifted in steps so that neighbours do not share a height
        List<V3> five = Formation.offsets(5, 6.0, 4.0);
        expect(five.get(0).y() == 0.0 && Math.abs(five.get(1).y() - Formation.LIFT_STEP) < 1e-9
                && Math.abs(five.get(2).y() - 2 * Formation.LIFT_STEP) < 1e-9 && five.get(3).y() == 0.0, "heights step up 0, 1, 2 and repeat");
        // the ring grows with the crowd
        double two = Math.hypot(Formation.offsets(2, 6, 0).get(0).x(), Formation.offsets(2, 6, 0).get(0).z());
        double ten = Math.hypot(Formation.offsets(10, 6, 0).get(0).x(), Formation.offsets(10, 6, 0).get(0).z());
        expect(ten > two, "ten dragons need a wider ring than two");
        near(two, 3.0, 1e-9, "two dragons 6 apart stand 3 from the middle");
        // a bigger minimum wins for small groups
        near(Math.hypot(Formation.offsets(2, 6, 9).get(0).x(), Formation.offsets(2, 6, 9).get(0).z()), 9.0, 1e-9, "minimum radius applies");
        // different spacing
        expect(Formation.closest(Formation.offsets(7, 12.0, 0)) >= 12.0 - 1e-9, "wider spacing is honoured");
        expect(Formation.closest(List.of()) == Double.POSITIVE_INFINITY, "no pair, no distance");
        expect(Formation.closest(List.of(new V3(0, 0, 0))) == Double.POSITIVE_INFINITY, "one slot, no distance");
    }

    static void freeSpots() {
        V3 base = new V3(10, 64, 20);
        List<V3> none = new ArrayList<>();
        expect(Formation.freeSpot(base, none, 6) == base, "with nobody there the base itself is used");
        // a dragon 5.9 blocks away is too close for a spacing of 6; 6.0 exactly is fine
        List<V3> close = new ArrayList<>();
        close.add(new V3(15.9, 64, 20));
        V3 moved = Formation.freeSpot(base, close, 6);
        expect(moved != base, "5.9 blocks from another dragon is too close for a spacing of 6");
        List<V3> exact = new ArrayList<>();
        exact.add(new V3(16, 64, 20));
        expect(Formation.freeSpot(base, exact, 6) == base, "exactly one spacing away is allowed");
        // the base is crowded: the pick is a ring spot, clear of everyone, and keeps the height
        List<V3> crowd = new ArrayList<>();
        crowd.add(new V3(10, 70, 20));
        crowd.add(new V3(13, 64, 20));
        V3 pick = Formation.freeSpot(base, crowd, 6);
        near(pick.y(), 64, 1e-12, "height is left alone");
        for (V3 other : crowd) {
            expect(Math.hypot(pick.x() - other.x(), pick.z() - other.z()) >= 6 - 1e-9,
                    "the pick keeps a full spacing from everyone (" + pick.x() + "," + pick.z() + ")");
        }
        near(Math.hypot(pick.x() - base.x(), pick.z() - base.z()), 6, 1e-9, "the first free place is on the first ring, one spacing out");
        // surrounded on the first ring: the search moves to the second
        List<V3> ring1 = new ArrayList<>();
        ring1.add(base);
        for (int k = 0; k < 8; k++) {
            double a = 2.0 * Math.PI * k / 8.0;
            ring1.add(new V3(base.x() + Math.cos(a) * 6, 64, base.z() + Math.sin(a) * 6));
        }
        V3 outer = Formation.freeSpot(base, ring1, 6);
        near(Math.hypot(outer.x() - base.x(), outer.z() - base.z()), 12, 1e-9, "with the first ring full the second is used");
        // nothing free within ten rings: the base comes back
        List<V3> wall = new ArrayList<>();
        for (int x = -80; x <= 80; x += 3) {
            for (int z = -80; z <= 80; z += 3) {
                wall.add(new V3(10 + x, 64, 20 + z));
            }
        }
        expect(Formation.freeSpot(base, wall, 6) == base, "if nothing within ten rings is free the base is returned");
    }

    static void slotAssignment() {
        Random random = new Random(11);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(new UUID(random.nextLong(), random.nextLong()));
        }
        Set<Integer> used = new HashSet<>();
        for (UUID id : ids) {
            int slot = Formation.slotOf(ids, id);
            expect(slot >= 0 && slot < 10, "every follower has a slot");
            expect(used.add(slot), "no two followers share a slot");
        }
        expect(used.size() == 10, "all ten slots are used");
        // the order the list arrives in changes nothing
        List<UUID> shuffled = new ArrayList<>(ids);
        java.util.Collections.shuffle(shuffled, new Random(3));
        for (UUID id : ids) {
            expect(Formation.slotOf(ids, id) == Formation.slotOf(shuffled, id), "slot does not depend on list order");
        }
        expect(Formation.slotOf(ids, UUID.randomUUID()) == -1, "a stranger has no slot");
        expect(Formation.slotFor(ids, UUID.randomUUID(), 6, 4) == null, "a stranger gets no spot");
        expect(Formation.slotFor(ids, ids.get(0), 6, 4) != null, "a follower gets a spot");
        // two followers: the two spots are opposite each other
        List<UUID> pair = ids.subList(0, 2);
        V3 first = Formation.slotFor(pair, pair.get(0), 6, 0);
        V3 second = Formation.slotFor(pair, pair.get(1), 6, 0);
        near(Math.hypot(first.x() - second.x(), first.z() - second.z()), 6.0, 1e-9, "a pair stands 6 apart");
        // order() leaves the caller's list alone
        List<UUID> copy = new ArrayList<>(ids);
        Formation.order(ids);
        expect(copy.equals(ids), "ordering does not reorder the caller's list");
    }

    // ------------------------------------------------------------------ the shape

    static void shape() {
        List<DragonModel.Box> boxes = DragonModel.boxes();
        expect(boxes.size() >= 15 && boxes.size() <= 30, "a sensible number of boxes (" + boxes.size() + ")");
        Set<String> ids = new HashSet<>();
        Set<DragonModel.Role> roles = new HashSet<>();
        for (DragonModel.Box b : boxes) {
            expect(ids.add(b.id()), "unique box id " + b.id());
            roles.add(b.role());
            expect(b.size().x() > 0 && b.size().y() > 0 && b.size().z() > 0, "box has volume " + b.id());
            expect(b.side() >= -1 && b.side() <= 1, "side is -1, 0 or 1");
        }
        expect(roles.size() == DragonModel.Role.values().length, "every role is used");
        int left = 0;
        int right = 0;
        for (DragonModel.Box b : boxes) {
            if (b.side() > 0) {
                left++;
            } else if (b.side() < 0) {
                right++;
            }
        }
        expect(left > 0 && left == right, "the two wings match (" + left + " and " + right + ")");
        // mirrored pairs really are mirrors
        for (DragonModel.Box b : boxes) {
            if (b.id().endsWith("L")) {
                DragonModel.Box twin = null;
                for (DragonModel.Box other : boxes) {
                    if (other.id().equals(b.id().substring(0, b.id().length() - 1) + "R")) {
                        twin = other;
                    }
                }
                expect(twin != null, b.id() + " has a right-hand twin");
                if (twin != null) {
                    near(twin.centre().x(), -b.centre().x(), 1e-9, b.id() + " mirrors in x");
                    near(twin.centre().y(), b.centre().y(), 1e-9, b.id() + " mirrors in y");
                    near(twin.centre().z(), b.centre().z(), 1e-9, b.id() + " mirrors in z");
                    near(twin.size().x(), b.size().x(), 1e-9, b.id() + " same width");
                }
            }
        }
        // the dragon stands on the ground and nothing sinks far below it
        double low = Double.POSITIVE_INFINITY;
        double longest = Double.NEGATIVE_INFINITY;
        double shortest = Double.POSITIVE_INFINITY;
        for (DragonModel.Placed p : DragonModel.place(0, 0, 1.0)) {
            low = Math.min(low, p.centre().y() - p.scale().y() / 2.0);
            longest = Math.max(longest, p.centre().z());
            shortest = Math.min(shortest, p.centre().z());
        }
        expect(low > -0.5, "nothing hangs far below the ground (" + low + ")");
        expect(longest > 5 && shortest < -5, "the model runs from a head in front to a tail behind");
    }

    // ------------------------------------------------------------------ placement

    /** The eight corners of a placed box, rebuilt from its translation, rotation and scale the way a block display draws a unit cube. */
    static List<V3> corners(DragonModel.Placed p) {
        List<V3> out = new ArrayList<>();
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    V3 scaled = new V3(x * p.scale().x(), y * p.scale().y(), z * p.scale().z());
                    out.add(p.translation().plus(p.rotation().rotate(scaled)));
                }
            }
        }
        return out;
    }

    static V3 middle(List<V3> points) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (V3 p : points) {
            x += p.x();
            y += p.y();
            z += p.z();
        }
        return new V3(x / points.size(), y / points.size(), z / points.size());
    }

    static DragonModel.Placed find(List<DragonModel.Placed> all, String id) {
        for (DragonModel.Placed p : all) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        throw new IllegalStateException("no box " + id);
    }

    static void placement() {
        for (double yaw : new double[] {0, 37, 90, -90, 180, -135}) {
            for (double flap : new double[] {0, 0.5, -1.0}) {
                for (double size : new double[] {0.2, 1.0, 2.5}) {
                    List<DragonModel.Placed> placed = DragonModel.place(yaw, flap, size);
                    expect(placed.size() == DragonModel.boxes().size(), "one placement per box");
                    for (DragonModel.Placed p : placed) {
                        V3 mid = middle(corners(p));
                        near(mid.x(), p.centre().x(), 1e-9, "the drawn box is centred where it should be (x) " + p.id());
                        near(mid.y(), p.centre().y(), 1e-9, "the drawn box is centred where it should be (y) " + p.id());
                        near(mid.z(), p.centre().z(), 1e-9, "the drawn box is centred where it should be (z) " + p.id());
                    }
                }
            }
        }
        // size scales every distance
        DragonModel.Placed big = find(DragonModel.place(0, 0, 2.0), "head");
        DragonModel.Placed small = find(DragonModel.place(0, 0, 1.0), "head");
        near(big.centre().z(), small.centre().z() * 2.0, 1e-9, "twice the size, twice the reach");
        near(big.scale().x(), small.scale().x() * 2.0, 1e-9, "twice the size, twice the width");
        // heading 0 faces +Z, 90 faces -X (the way Minecraft counts yaw)
        expect(find(DragonModel.place(0, 0, 1), "head").centre().z() > 5, "yaw 0: the head is toward +Z");
        V3 east = find(DragonModel.place(90, 0, 1), "head").centre();
        expect(east.x() < -5 && Math.abs(east.z()) < 1e-6, "yaw 90: the head is toward -X (" + east + ")");
        V3 north = find(DragonModel.place(180, 0, 1), "head").centre();
        expect(north.z() < -5, "yaw 180: the head is toward -Z");
        V3 west = find(DragonModel.place(-90, 0, 1), "head").centre();
        expect(west.x() > 5, "yaw -90: the head is toward +X");
        // turning never changes height or the distances between boxes
        List<DragonModel.Placed> a = DragonModel.place(0, 0.3, 1);
        List<DragonModel.Placed> b = DragonModel.place(123, 0.3, 1);
        for (int i = 0; i < a.size(); i++) {
            near(a.get(i).centre().y(), b.get(i).centre().y(), 1e-9, "turning keeps height " + a.get(i).id());
            near(Math.hypot(a.get(i).centre().x(), a.get(i).centre().z()), Math.hypot(b.get(i).centre().x(), b.get(i).centre().z()), 1e-9,
                    "turning keeps the distance from the middle " + a.get(i).id());
        }
        // wings: a positive flap lifts both tips, a negative one lowers both
        double restL = find(DragonModel.place(0, 0, 1), "wingArmL").centre().y();
        double restR = find(DragonModel.place(0, 0, 1), "wingArmR").centre().y();
        double upL = find(DragonModel.place(0, 0.5, 1), "wingArmL").centre().y();
        double upR = find(DragonModel.place(0, 0.5, 1), "wingArmR").centre().y();
        double downL = find(DragonModel.place(0, -0.5, 1), "wingArmL").centre().y();
        double downR = find(DragonModel.place(0, -0.5, 1), "wingArmR").centre().y();
        expect(upL > restL && upR > restR, "a positive flap lifts both wings");
        expect(downL < restL && downR < restR, "a negative flap lowers both wings");
        near(upL, upR, 1e-9, "the wings beat together");
        // the shoulder stays put whatever the wing does
        double armLength = Math.hypot(find(DragonModel.place(0, 0, 1), "wingArmL").centre().x() - 1.2,
                find(DragonModel.place(0, 0, 1), "wingArmL").centre().y() - 3.1);
        double flapped = Math.hypot(find(DragonModel.place(0, 0.9, 1), "wingArmL").centre().x() - 1.2,
                find(DragonModel.place(0, 0.9, 1), "wingArmL").centre().y() - 3.1);
        near(flapped, armLength, 1e-9, "the wing swings about the shoulder without stretching");
        // the body does not move when the wings do
        near(find(DragonModel.place(0, 0, 1), "body").centre().y(), find(DragonModel.place(0, 0.9, 1), "body").centre().y(), 1e-12, "body ignores flap");
        near(find(DragonModel.place(0, 0, 1), "tail2").centre().z(), find(DragonModel.place(0, 0.9, 1), "tail2").centre().z(), 1e-12, "tail ignores flap");
    }

    static void flapping() {
        expect(DragonModel.flapAngle(0, false, true) < 0, "grounded wings are folded down");
        near(DragonModel.flapAngle(5, false, false), DragonModel.flapAngle(900, false, true), 1e-12, "a grounded dragon's wings never move");
        double maxMoving = 0;
        double maxHover = 0;
        double minMoving = 0;
        for (long t = 0; t < 400; t++) {
            maxMoving = Math.max(maxMoving, DragonModel.flapAngle(t, true, true));
            minMoving = Math.min(minMoving, DragonModel.flapAngle(t, true, true));
            maxHover = Math.max(maxHover, DragonModel.flapAngle(t, true, false));
        }
        expect(maxMoving > 0.5 && maxMoving <= 0.6 + 1e-9, "a flying dragon beats up to 0.6 rad (" + maxMoving + ")");
        expect(minMoving < -0.5 && minMoving >= -0.6 - 1e-9, "and down to -0.6 rad (" + minMoving + ")");
        expect(maxHover > 0.2 && maxHover < maxMoving, "hovering is gentler than flying");
    }
}
