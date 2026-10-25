package com.nexuscraft.nexushatchlings;

/** What the movement maths needs to know about the world: whether a block is solid. */
@FunctionalInterface
public interface Terrain {
    boolean solid(int x, int y, int z);
}
