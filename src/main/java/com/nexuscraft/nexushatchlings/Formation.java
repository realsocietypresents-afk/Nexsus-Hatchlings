package com.nexuscraft.nexushatchlings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Where each of an owner's following dragons waits, so they stand apart instead of all piling onto the owner's feet.
 * One dragon keeps the old behaviour (it comes to the owner); two or more spread out evenly on a ring around the owner,
 * the ring just wide enough that neighbours are at least {@code spacing} blocks apart. Pure maths, no server classes.
 */
public final class Formation {

    private Formation() {
    }

    /** How much higher each next slot of a flying dragon hovers, in blocks, in a repeating 0..2 pattern. */
    public static final double LIFT_STEP = 1.5;

    /**
     * The offsets (x and z from the owner; y is extra height for a flyer) of {@code count} slots.
     *
     * @param count     how many dragons are following
     * @param spacing   the least distance between two neighbours
     * @param minRadius the ring is never tighter than this
     */
    public static List<V3> offsets(int count, double spacing, double minRadius) {
        List<V3> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        if (count == 1) {
            out.add(new V3(0, 0, 0));
            return out;
        }
        // The chord between neighbours on a ring of radius r is 2 r sin(pi / n); solve for the chord = spacing.
        double radius = Math.max(minRadius, spacing / (2.0 * Math.sin(Math.PI / count)));
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            out.add(new V3(Math.cos(angle) * radius, (i % 3) * LIFT_STEP, Math.sin(angle) * radius));
        }
        return out;
    }

    /** Followers in a fixed order (by id), so a dragon keeps its slot from one tick to the next. */
    public static List<UUID> order(List<UUID> followers) {
        List<UUID> sorted = new ArrayList<>(followers);
        sorted.sort(Comparator.comparing(UUID::toString));
        return sorted;
    }

    /** The slot number of {@code me} among the followers, or -1 when it is not one of them. */
    public static int slotOf(List<UUID> followers, UUID me) {
        return order(followers).indexOf(me);
    }

    /** The slot for one dragon, or null when it is not a follower. */
    public static V3 slotFor(List<UUID> followers, UUID me, double spacing, double minRadius) {
        int index = slotOf(followers, me);
        if (index < 0) {
            return null;
        }
        return offsets(followers.size(), spacing, minRadius).get(index);
    }

    /**
     * A place for something to stand near {@code base} that is at least {@code spacing} from everything in {@code taken}: the base
     * itself when it is free, otherwise the first free place on rings 1, 2, 3 ... times the spacing around it (eight places a ring).
     * Height is left alone. Falls back to the base if nothing within ten rings is free.
     */
    public static V3 freeSpot(V3 base, List<V3> taken, double spacing) {
        if (isFree(base, taken, spacing)) {
            return base;
        }
        for (int ring = 1; ring <= 10; ring++) {
            for (int k = 0; k < 8; k++) {
                double angle = 2.0 * Math.PI * k / 8.0;
                V3 candidate = new V3(base.x() + Math.cos(angle) * spacing * ring, base.y(), base.z() + Math.sin(angle) * spacing * ring);
                if (isFree(candidate, taken, spacing)) {
                    return candidate;
                }
            }
        }
        return base;
    }

    private static boolean isFree(V3 spot, List<V3> taken, double spacing) {
        for (V3 other : taken) {
            double dx = spot.x() - other.x();
            double dz = spot.z() - other.z();
            if (Math.sqrt(dx * dx + dz * dz) < spacing - 1e-9) {
                return false;
            }
        }
        return true;
    }

    /** Least distance between any two of the slots, ignoring height (infinity for fewer than two). */
    public static double closest(List<V3> slots) {
        double best = Double.POSITIVE_INFINITY;
        for (int a = 0; a < slots.size(); a++) {
            for (int b = a + 1; b < slots.size(); b++) {
                double dx = slots.get(a).x() - slots.get(b).x();
                double dz = slots.get(a).z() - slots.get(b).z();
                best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
            }
        }
        return best;
    }
}
