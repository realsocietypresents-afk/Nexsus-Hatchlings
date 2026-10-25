package com.nexuscraft.nexushatchlings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Picks who a dragon attacks when its owner gives the order. Pure: the caller describes the players, this decides. */
public final class Targeting {

    private Targeting() {
    }

    /** NEAREST: the closest valid player. NAMED: the one player named. ALL: every valid player in range. */
    public enum Mode { NEAREST, NAMED, ALL }

    /** Why nobody was picked. */
    public enum Reason { OK, NOBODY_IN_RANGE, NOT_FOUND, TOO_FAR, OWNER, ALLY, CANNOT_BE_HURT }

    /** One player as the dragon sees them. {@code hurtable} is false for creative, spectator and dead players. */
    public record Candidate(UUID id, String name, double distance, boolean owner, boolean ally, boolean hurtable) {
    }

    public record Result(Reason reason, List<Candidate> targets) {
        public boolean ok() {
            return reason == Reason.OK;
        }
    }

    public static Result pick(List<Candidate> everyone, Mode mode, String name, double range) {
        if (mode == Mode.NAMED) {
            return named(everyone, name, range);
        }
        List<Candidate> valid = new ArrayList<>();
        for (Candidate c : everyone) {
            if (!c.owner() && !c.ally() && c.hurtable() && c.distance() <= range) {
                valid.add(c);
            }
        }
        valid.sort(Comparator.comparingDouble(Candidate::distance));
        if (valid.isEmpty()) {
            return new Result(Reason.NOBODY_IN_RANGE, List.of());
        }
        if (mode == Mode.NEAREST) {
            return new Result(Reason.OK, List.of(valid.get(0)));
        }
        return new Result(Reason.OK, valid);
    }

    private static Result named(List<Candidate> everyone, String name, double range) {
        if (name == null) {
            return new Result(Reason.NOT_FOUND, List.of());
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (Candidate c : everyone) {
            if (!c.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                continue;
            }
            if (c.owner()) {
                return new Result(Reason.OWNER, List.of());
            }
            if (c.ally()) {
                return new Result(Reason.ALLY, List.of());
            }
            if (!c.hurtable()) {
                return new Result(Reason.CANNOT_BE_HURT, List.of());
            }
            if (c.distance() > range) {
                return new Result(Reason.TOO_FAR, List.of());
            }
            return new Result(Reason.OK, List.of(c));
        }
        return new Result(Reason.NOT_FOUND, List.of());
    }
}
