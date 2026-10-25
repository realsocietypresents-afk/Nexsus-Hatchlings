package com.nexuscraft.nexushatchlings;

/** The movement maths for dragons, with no server classes in it so it can be tested on its own. Angles are Minecraft's: yaw 0 faces +Z, 90 faces -X. */
public final class DragonMath {

    private DragonMath() {
    }

    /** The point reached by moving from {@code from} towards {@code to} by at most {@code maxStep}. */
    public static V3 stepToward(V3 from, V3 to, double maxStep) {
        V3 delta = to.minus(from);
        double dist = delta.length();
        if (dist <= 1.0e-9 || maxStep <= 0.0) {
            return from;
        }
        if (dist <= maxStep) {
            return to;
        }
        return from.plus(delta.times(maxStep / dist));
    }

    /** Wraps an angle in degrees into [-180, 180). */
    public static double normalizeYaw(double yaw) {
        double wrapped = yaw % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    /** The yaw that faces from one point to another (looking along the ground). */
    public static double yawToward(V3 from, V3 to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        if (Math.abs(dx) < 1.0e-9 && Math.abs(dz) < 1.0e-9) {
            return 0.0;
        }
        return normalizeYaw(Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** The unit direction a player looking with this yaw and pitch is facing. */
    public static V3 lookVector(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double horizontal = Math.cos(pitch);
        return new V3(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal);
    }

    /** The yaw a dragon model needs so that it faces where its rider looks (the model faces away from its yaw, hence an offset). */
    public static double dragonYaw(double riderYaw, double offset) {
        return normalizeYaw(riderYaw + offset);
    }

    /**
     * The feet height a walker can stand at in this column, searching from {@code up} blocks above {@code fromY} down to
     * {@code down} blocks below it. A spot works when the block below is solid and {@code headroom} blocks from the feet
     * up are clear. NaN when there is none.
     */
    public static double groundY(Terrain terrain, int x, int z, int fromY, int up, int down, int headroom) {
        for (int y = fromY + up; y >= fromY - down; y--) {
            if (!terrain.solid(x, y - 1, z)) {
                continue;
            }
            boolean clear = true;
            for (int h = 0; h < headroom; h++) {
                if (terrain.solid(x, y + h, z)) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return y;
            }
        }
        return Double.NaN;
    }

    /** True when the point and {@code headroom} blocks above it are all open air. */
    public static boolean clear(Terrain terrain, V3 point, int headroom) {
        int x = (int) Math.floor(point.x());
        int y = (int) Math.floor(point.y());
        int z = (int) Math.floor(point.z());
        for (int h = 0; h < headroom; h++) {
            if (terrain.solid(x, y + h, z)) {
                return false;
            }
        }
        return true;
    }

    /** Lowers {@code currentY} towards {@code groundY} by at most {@code maxDrop}, never below the ground. */
    public static double descend(double currentY, double groundY, double maxDrop) {
        if (currentY <= groundY) {
            return groundY;
        }
        return Math.max(groundY, currentY - Math.max(0.0, maxDrop));
    }

    /** Keeps a height inside the world. */
    public static double clampY(double y, double minY, double maxY) {
        return Math.max(minY, Math.min(maxY, y));
    }
}
