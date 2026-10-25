package com.nexuscraft.nexushatchlings;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a dragon built from plain boxes, as plain numbers, so the model can be checked without a server. Each box is
 * one block-display entity in the game: this class says where every box goes, how it is turned and how big it is for a given
 * heading, wing angle and size. Colours are not here; each box only says which {@link Role} it plays and the game side picks a
 * block for that role in the dragon's colour.
 *
 * <p>The model is drawn facing +Z with +Y up (so +X is its left), standing on y = 0, about nineteen blocks nose to tail at size 1.
 */
public final class DragonModel {

    private DragonModel() {
    }

    /** What a box is made of, in the dragon's colour. */
    public enum Role { BODY, ACCENT, WING, TRIM, EYE }

    /** A rotation as a unit quaternion. */
    public record Quat(double x, double y, double z, double w) {

        public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

        public static Quat axis(double ax, double ay, double az, double radians) {
            double half = radians / 2.0;
            double s = Math.sin(half);
            return new Quat(ax * s, ay * s, az * s, Math.cos(half));
        }

        /** This rotation applied after {@code o} (as in Hamilton's product this * o). */
        public Quat times(Quat o) {
            return new Quat(
                    w * o.x + x * o.w + y * o.z - z * o.y,
                    w * o.y - x * o.z + y * o.w + z * o.x,
                    w * o.z + x * o.y - y * o.x + z * o.w,
                    w * o.w - x * o.x - y * o.y - z * o.z);
        }

        /** Turns a point or direction. */
        public V3 rotate(V3 v) {
            // v' = v + 2 w (q x v) + 2 q x (q x v)
            double cx = y * v.z() - z * v.y();
            double cy = z * v.x() - x * v.z();
            double cz = x * v.y() - y * v.x();
            double dx = y * cz - z * cy;
            double dy = z * cx - x * cz;
            double dz = x * cy - y * cx;
            return new V3(v.x() + 2.0 * (w * cx + dx), v.y() + 2.0 * (w * cy + dy), v.z() + 2.0 * (w * cz + dz));
        }
    }

    /**
     * One box of the model. {@code centre} is where its middle sits; a box with a {@code side} of +1 or -1 is part of a wing and
     * swings about {@code pivot} (the shoulder), a +X wing going up for a positive angle and a -X wing mirroring it.
     */
    public record Box(String id, Role role, V3 centre, V3 size, Quat turn, int side, V3 pivot) {
    }

    /** One box ready to draw: the middle of it, and the three numbers a block display wants (translation, rotation, scale). */
    public record Placed(String id, Role role, V3 centre, V3 translation, Quat rotation, V3 scale) {
    }

    private static final V3 NONE = new V3(0, 0, 0);

    private static Box box(String id, Role role, double cx, double cy, double cz, double sx, double sy, double sz, double pitchDegrees) {
        return new Box(id, role, new V3(cx, cy, cz), new V3(sx, sy, sz), Quat.axis(1, 0, 0, Math.toRadians(pitchDegrees)), 0, NONE);
    }

    private static Box wing(String id, Role role, int side, V3 pivot, double relX, double relY, double relZ, double sx, double sy, double sz) {
        V3 centre = new V3(pivot.x() + side * relX, pivot.y() + relY, pivot.z() + relZ);
        return new Box(id, role, centre, new V3(sx, sy, sz), Quat.IDENTITY, side, pivot);
    }

    private static final List<Box> BOXES = build();

    private static List<Box> build() {
        List<Box> b = new ArrayList<>();
        b.add(box("body", Role.BODY, 0, 2.2, 0, 2.4, 2.0, 4.5, 0));
        b.add(box("belly", Role.ACCENT, 0, 1.45, 0.2, 1.8, 0.5, 3.8, 0));
        b.add(box("neck1", Role.BODY, 0, 3.0, 2.9, 1.2, 1.2, 1.8, -25));
        b.add(box("neck2", Role.BODY, 0, 3.45, 4.53, 1.0, 1.0, 1.6, -5));
        b.add(box("head", Role.BODY, 0, 3.54, 6.13, 1.3, 1.0, 1.6, 0));
        b.add(box("snout", Role.ACCENT, 0, 3.35, 7.4, 0.8, 0.5, 1.0, 0));
        b.add(box("eyeL", Role.EYE, 0.5, 3.8, 6.7, 0.2, 0.2, 0.2, 0));
        b.add(box("eyeR", Role.EYE, -0.5, 3.8, 6.7, 0.2, 0.2, 0.2, 0));
        b.add(box("hornL", Role.TRIM, 0.45, 4.2, 5.55, 0.2, 0.2, 1.0, 30));
        b.add(box("hornR", Role.TRIM, -0.45, 4.2, 5.55, 0.2, 0.2, 1.0, 30));
        b.add(box("tail1", Role.BODY, 0, 2.0, -3.3, 1.6, 1.4, 2.6, -8));
        b.add(box("tail2", Role.BODY, 0, 1.75, -5.8, 1.1, 1.0, 2.6, -8));
        b.add(box("tail3", Role.ACCENT, 0, 1.5, -8.1, 0.7, 0.7, 2.4, -10));
        b.add(box("legFL", Role.BODY, 1.0, 0.8, 1.4, 0.7, 1.6, 0.8, 0));
        b.add(box("legFR", Role.BODY, -1.0, 0.8, 1.4, 0.7, 1.6, 0.8, 0));
        b.add(box("legBL", Role.BODY, 1.0, 0.8, -1.4, 0.7, 1.6, 0.8, 0));
        b.add(box("legBR", Role.BODY, -1.0, 0.8, -1.4, 0.7, 1.6, 0.8, 0));
        for (int side : new int[] {1, -1}) {
            V3 shoulder = new V3(side * 1.2, 3.1, 0.6);
            String tag = side > 0 ? "L" : "R";
            b.add(wing("wingArm" + tag, Role.ACCENT, side, shoulder, 3.4, 0.0, 0.0, 6.8, 0.35, 0.35));
            b.add(wing("wingSail" + tag, Role.WING, side, shoulder, 3.4, -0.1, -1.6, 6.4, 0.08, 3.0));
        }
        return List.copyOf(b);
    }

    /** Every box of the model, unrotated. */
    public static List<Box> boxes() {
        return BOXES;
    }

    /** How the wings should be held: folded on the ground, beating hard when flying somewhere, a lazy beat when hovering. */
    public static double flapAngle(long ticks, boolean canFly, boolean moving) {
        if (!canFly) {
            return -1.0;
        }
        return moving ? 0.6 * Math.sin(ticks * 0.5) : 0.25 * Math.sin(ticks * 0.3);
    }

    /**
     * Every box placed for a dragon facing a heading.
     *
     * @param headingYaw the Minecraft yaw the dragon's nose points along (0 = +Z, 90 = -X), in degrees
     * @param flap       the wing angle in radians (positive lifts the wings)
     * @param size       1.0 is the model as drawn; the game passes the dragon's growth and the config multiplier here
     */
    public static List<Placed> place(double headingYaw, double flap, double size) {
        Quat yaw = Quat.axis(0, 1, 0, -Math.toRadians(headingYaw));
        List<Placed> out = new ArrayList<>(BOXES.size());
        for (Box box : BOXES) {
            V3 centre = box.centre();
            Quat turn = box.turn();
            if (box.side() != 0) {
                Quat swing = Quat.axis(0, 0, 1, box.side() * flap);
                centre = box.pivot().plus(swing.rotate(centre.minus(box.pivot())));
                turn = swing.times(turn);
            }
            Quat rotation = yaw.times(turn);
            V3 world = yaw.rotate(centre).times(size);
            V3 scale = box.size().times(size);
            V3 translation = world.minus(rotation.rotate(scale.times(0.5)));
            out.add(new Placed(box.id(), box.role(), world, translation, rotation, scale));
        }
        return out;
    }
}
