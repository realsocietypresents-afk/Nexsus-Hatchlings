package com.nexuscraft.nexushatchlings;

/** A plain point or direction in space, so the movement maths can be tested without a server. */
public record V3(double x, double y, double z) {

    public V3 plus(V3 other) {
        return new V3(x + other.x, y + other.y, z + other.z);
    }

    public V3 minus(V3 other) {
        return new V3(x - other.x, y - other.y, z - other.z);
    }

    public V3 times(double factor) {
        return new V3(x * factor, y * factor, z * factor);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double distance(V3 other) {
        return minus(other).length();
    }
}
