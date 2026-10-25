package com.nexuscraft.nexushatchlings;

/** How grown a dragon is decides what it can do: a hatchling only walks, a fledgling flies and carries its owner, an adult also attacks. */
public enum DragonStage {
    HATCHLING("Hatchling"),
    FLEDGLING("Fledgling"),
    ADULT("Adult");

    private final String display;

    DragonStage(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public boolean canFly() {
        return this != HATCHLING;
    }

    public boolean canAttack() {
        return this == ADULT;
    }

    /**
     * The stage for how far along growth is.
     *
     * @param progress growth from 0.0 to 1.0
     * @param flyAt    growth at which it can fly and be ridden
     * @param adultAt  growth at which it counts as an adult and can attack
     */
    public static DragonStage of(double progress, double flyAt, double adultAt) {
        if (progress >= adultAt && progress >= flyAt) {
            return ADULT;
        }
        if (progress >= flyAt) {
            return FLEDGLING;
        }
        return HATCHLING;
    }
}
