package com.nexuscraft.nexushatchlings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexushatchlings.Kit.expect;
import static com.nexuscraft.nexushatchlings.Kit.near;

/** The parts with no server in them: colours, stages, the movement maths, command words and who gets picked as a target. */
public class PureTest {

    public static void main(String[] args) {
        colours();
        stages();
        vectors();
        yaw();
        ground();
        steps();
        words();
        targeting();
        Kit.finish();
    }

    static void colours() {
        DragonColor[] all = DragonColor.values();
        expect(all.length == 16, "sixteen colours");
        Set<String> ids = new HashSet<>();
        Set<String> dyes = new HashSet<>();
        for (DragonColor c : all) {
            expect(ids.add(c.id()), "unique id " + c.id());
            expect(dyes.add(c.dye()), "unique dye " + c.dye());
            expect(c.id().equals(c.id().toLowerCase()), "id is lower case " + c.id());
            expect(c.display().length() > 2 && Character.isUpperCase(c.display().charAt(0)), "display name " + c.display());
            expect(c.dye().endsWith("_DYE"), "dye is a dye " + c.dye());
            expect("0123456789abcdef".indexOf(c.chatCode()) >= 0, "chat code is a colour " + c.id());
            expect(c.red() >= 0 && c.red() <= 255 && c.green() >= 0 && c.green() <= 255 && c.blue() >= 0 && c.blue() <= 255, "rgb range " + c.id());
            expect(DragonColor.byId(c.id()) == c, "byId " + c.id());
            expect(DragonColor.byId(c.id().toUpperCase()) == c, "byId ignores case " + c.id());
            expect(DragonColor.byId("  " + c.id() + " ") == c, "byId trims " + c.id());
            expect(DragonColor.byDye(c.dye()) == c, "byDye " + c.dye());
            expect(c.teamColor() != null && !c.teamColor().isEmpty(), "team colour named " + c.id());
        }
        expect(DragonColor.byId(null) == null, "byId null");
        expect(DragonColor.byId("") == null, "byId empty");
        expect(DragonColor.byId("plaid") == null, "byId unknown");
        expect(DragonColor.byDye(null) == null, "byDye null");
        expect(DragonColor.byDye("STONE") == null, "byDye non-dye");
        expect(DragonColor.byDye("RED_DYE") == DragonColor.CRIMSON, "red dye is crimson");
        expect(DragonColor.byDye("BLACK_DYE") == DragonColor.ONYX, "black dye is onyx");
        expect(DragonColor.byDye("WHITE_DYE") == DragonColor.IVORY, "white dye is ivory");
        Random r = new Random(5);
        Set<DragonColor> seen = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            DragonColor c = DragonColor.random(r);
            expect(c != null, "random colour exists");
            seen.add(c);
        }
        expect(seen.size() == 16, "random reaches every colour (" + seen.size() + ")");
    }

    static void stages() {
        expect(DragonStage.of(0.0, 0.5, 1.0) == DragonStage.HATCHLING, "0 is hatchling");
        expect(DragonStage.of(0.4999, 0.5, 1.0) == DragonStage.HATCHLING, "just under fly is hatchling");
        expect(DragonStage.of(0.5, 0.5, 1.0) == DragonStage.FLEDGLING, "exactly fly is fledgling");
        expect(DragonStage.of(0.9999, 0.5, 1.0) == DragonStage.FLEDGLING, "just under adult is fledgling");
        expect(DragonStage.of(1.0, 0.5, 1.0) == DragonStage.ADULT, "exactly adult is adult");
        expect(DragonStage.of(0.5, 0.5, 0.5) == DragonStage.ADULT, "fly and adult together: adult");
        expect(DragonStage.of(0.3, 0.2, 0.2) == DragonStage.ADULT, "past both: adult");
        expect(DragonStage.of(0.1, 0.2, 0.2) == DragonStage.HATCHLING, "before both: hatchling");
        expect(DragonStage.of(0.7, 0.8, 0.6) == DragonStage.HATCHLING, "adult before fly (bad config) still needs fly first");
        expect(!DragonStage.HATCHLING.canFly() && DragonStage.FLEDGLING.canFly() && DragonStage.ADULT.canFly(), "who can fly");
        expect(!DragonStage.HATCHLING.canAttack() && !DragonStage.FLEDGLING.canAttack() && DragonStage.ADULT.canAttack(), "who can attack");
        expect(DragonStage.HATCHLING.display().equals("Hatchling") && DragonStage.FLEDGLING.display().equals("Fledgling")
                && DragonStage.ADULT.display().equals("Adult"), "display names");
    }

    static void vectors() {
        V3 a = new V3(1, 2, 3);
        V3 b = new V3(4, 6, 3);
        expect(a.plus(b).equals(new V3(5, 8, 6)), "plus");
        expect(b.minus(a).equals(new V3(3, 4, 0)), "minus");
        expect(a.times(2).equals(new V3(2, 4, 6)), "times");
        near(new V3(3, 4, 0).length(), 5.0, 1e-9, "length");
        near(a.distance(b), 5.0, 1e-9, "distance");
        near(a.distance(a), 0.0, 1e-9, "distance to self");
        near(new V3(0, 0, 0).length(), 0.0, 1e-9, "zero length");
    }

    static void yaw() {
        near(DragonMath.normalizeYaw(0), 0, 1e-9, "yaw 0");
        near(DragonMath.normalizeYaw(90), 90, 1e-9, "yaw 90");
        near(DragonMath.normalizeYaw(179.9), 179.9, 1e-9, "yaw 179.9");
        near(DragonMath.normalizeYaw(180), -180, 1e-9, "yaw 180 wraps to -180");
        near(DragonMath.normalizeYaw(-180), -180, 1e-9, "yaw -180 stays");
        near(DragonMath.normalizeYaw(190), -170, 1e-9, "yaw 190");
        near(DragonMath.normalizeYaw(-190), 170, 1e-9, "yaw -190");
        near(DragonMath.normalizeYaw(360), 0, 1e-9, "yaw 360");
        near(DragonMath.normalizeYaw(-360), 0, 1e-9, "yaw -360");
        near(DragonMath.normalizeYaw(725), 5, 1e-9, "yaw 725");
        near(DragonMath.normalizeYaw(-725), -5, 1e-9, "yaw -725");
        V3 o = new V3(0, 0, 0);
        near(DragonMath.yawToward(o, new V3(0, 0, 5)), 0, 1e-9, "toward +z is yaw 0");
        near(DragonMath.yawToward(o, new V3(-5, 0, 0)), 90, 1e-9, "toward -x is yaw 90");
        near(DragonMath.yawToward(o, new V3(5, 0, 0)), -90, 1e-9, "toward +x is yaw -90");
        near(Math.abs(DragonMath.yawToward(o, new V3(0, 0, -5))), 180, 1e-9, "toward -z is yaw 180");
        near(DragonMath.yawToward(o, new V3(3, 9, 3)), -45, 1e-9, "height does not change yaw");
        near(DragonMath.yawToward(o, o), 0, 1e-9, "no direction gives 0");
        near(DragonMath.yawToward(o, new V3(1.0e-12, 5, -1.0e-12)), 0, 1e-9, "a hair off the same column gives 0");
        near(DragonMath.dragonYaw(0, 180), -180, 1e-9, "model offset 0+180");
        near(DragonMath.dragonYaw(90, 180), -90, 1e-9, "model offset 90+180");
        near(DragonMath.dragonYaw(-100, 180), 80, 1e-9, "model offset -100+180");
        near(DragonMath.dragonYaw(45, 0), 45, 1e-9, "model offset 0");
        V3 look = DragonMath.lookVector(0, 0);
        near(look.x(), 0, 1e-9, "look south x");
        near(look.y(), 0, 1e-9, "look south y");
        near(look.z(), 1, 1e-9, "look south z");
        V3 west = DragonMath.lookVector(90, 0);
        near(west.x(), -1, 1e-9, "yaw 90 looks -x");
        near(west.z(), 0, 1e-9, "yaw 90 z");
        V3 up = DragonMath.lookVector(0, -90);
        near(up.y(), 1, 1e-9, "pitch -90 looks up");
        V3 down = DragonMath.lookVector(0, 90);
        near(down.y(), -1, 1e-9, "pitch 90 looks down");
        near(DragonMath.lookVector(33, 21).length(), 1, 1e-9, "look vector is a unit");
        near(DragonMath.lookVector(0, 45).y(), -Math.sin(Math.toRadians(45)), 1e-9, "pitch 45");
        near(DragonMath.lookVector(0, 45).z(), Math.cos(Math.toRadians(45)), 1e-9, "pitch 45 horizontal");
    }

    static Terrain floor(int level) {
        return (x, y, z) -> y < level;
    }

    static void ground() {
        Terrain flat = floor(64);
        near(DragonMath.groundY(flat, 0, 0, 64, 1, 3, 2), 64, 1e-9, "flat ground at its own height");
        near(DragonMath.groundY(flat, 0, 0, 66, 1, 3, 2), 64, 1e-9, "flat ground below the start");
        near(DragonMath.groundY(flat, 0, 0, 63, 1, 3, 2), 64, 1e-9, "ground one above the start is found");
        expect(Double.isNaN(DragonMath.groundY(flat, 0, 0, 60, 1, 3, 2)), "starting inside the ground finds nothing");
        expect(Double.isNaN(DragonMath.groundY(flat, 0, 0, 80, 1, 3, 2)), "too high above: nothing");
        expect(Double.isNaN(DragonMath.groundY(flat, 0, 0, 50, 1, 3, 2)), "ground deeper than the climb: nothing (already underground)");
        // a one-block step up
        Terrain step = (x, y, z) -> y < 64 || (x >= 5 && y < 65);
        near(DragonMath.groundY(step, 5, 0, 64, 1, 3, 2), 65, 1e-9, "step up of one is allowed");
        Terrain wall = (x, y, z) -> y < 64 || (x >= 5 && y < 67);
        expect(Double.isNaN(DragonMath.groundY(wall, 5, 0, 64, 1, 3, 2)), "a three-high wall blocks");
        near(DragonMath.groundY(wall, 4, 0, 64, 1, 3, 2), 64, 1e-9, "next to the wall is fine");
        // a drop
        Terrain drop = (x, y, z) -> x < 5 ? y < 64 : y < 62;
        near(DragonMath.groundY(drop, 5, 0, 64, 1, 3, 2), 62, 1e-9, "a drop of two is followed");
        Terrain cliff = (x, y, z) -> x < 5 ? y < 64 : y < 50;
        expect(Double.isNaN(DragonMath.groundY(cliff, 5, 0, 64, 1, 3, 2)), "a cliff is refused");
        // headroom
        Terrain roof = (x, y, z) -> y < 64 || y == 65;
        expect(Double.isNaN(DragonMath.groundY(roof, 0, 0, 64, 1, 3, 2)), "a low roof blocks a tall walker");
        near(DragonMath.groundY(roof, 0, 0, 64, 1, 3, 1), 64, 1e-9, "a short walker fits under it");
        near(DragonMath.groundY(roof, 0, 0, 64, 1, 3, 0), 64, 1e-9, "headroom 0 never blocks");
        // the highest valid spot wins: a ledge above an open floor
        Terrain two = (x, y, z) -> y < 60 || y == 63;
        near(DragonMath.groundY(two, 0, 0, 63, 1, 3, 1), 64, 1e-9, "the upper surface is chosen");
        near(DragonMath.groundY(two, 0, 0, 62, 1, 3, 1), 60, 1e-9, "a ledge more than a step up is not climbed");
        // clear
        Terrain pillar = (x, y, z) -> x == 0 && z == 0 && y >= 70 && y <= 71;
        expect(DragonMath.clear(pillar, new V3(0.5, 66, 0.5), 3), "clear below the pillar");
        expect(!DragonMath.clear(pillar, new V3(0.5, 68, 0.5), 3), "not clear when the block above is within headroom");
        expect(DragonMath.clear(pillar, new V3(0.5, 68, 0.5), 2), "clear when headroom stops short");
        expect(!DragonMath.clear(pillar, new V3(0.5, 70.9, 0.5), 1), "inside the block is not clear");
        expect(DragonMath.clear(pillar, new V3(5.5, 70, 5.5), 3), "elsewhere is clear");
        Terrain neg = (x, y, z) -> x == -1 && y == 10 && z == -1;
        expect(!DragonMath.clear(neg, new V3(-0.5, 10.2, -0.5), 1), "negative coordinates floor to the right block");
        expect(DragonMath.clear(neg, new V3(0.5, 10.2, 0.5), 1), "and not to the wrong one");
        expect(DragonMath.clear(neg, new V3(-0.5, 10.2, -0.5), 0), "zero headroom is always clear");
    }

    static void steps() {
        V3 from = new V3(0, 0, 0);
        V3 to = new V3(10, 0, 0);
        expect(DragonMath.stepToward(from, to, 3).equals(new V3(3, 0, 0)), "a step along the line");
        expect(DragonMath.stepToward(from, to, 10).equals(to), "a step that just arrives");
        expect(DragonMath.stepToward(from, to, 50).equals(to), "a step past does not overshoot");
        expect(DragonMath.stepToward(from, to, 0).equals(from), "no step with no speed");
        expect(DragonMath.stepToward(from, to, -4).equals(from), "no step with negative speed");
        expect(DragonMath.stepToward(from, from, 3).equals(from), "no step when already there");
        V3 diag = DragonMath.stepToward(from, new V3(3, 4, 0), 2.5);
        near(diag.x(), 1.5, 1e-9, "diagonal x");
        near(diag.y(), 2.0, 1e-9, "diagonal y");
        near(DragonMath.descend(70, 64, 2), 68, 1e-9, "descend by the allowance");
        near(DragonMath.descend(65, 64, 2), 64, 1e-9, "descend stops at the ground");
        near(DragonMath.descend(64, 64, 2), 64, 1e-9, "already on the ground");
        near(DragonMath.descend(60, 64, 2), 64, 1e-9, "below the ground is lifted to it");
        near(DragonMath.descend(70, 64, -3), 70, 1e-9, "negative drop does nothing");
        near(DragonMath.clampY(5, 0, 10), 5, 1e-9, "clamp inside");
        near(DragonMath.clampY(-5, 0, 10), 0, 1e-9, "clamp low");
        near(DragonMath.clampY(15, 0, 10), 10, 1e-9, "clamp high");
    }

    static void words() {
        expect(CommandWords.valid("vaelrix"), "a default word is valid");
        expect(CommandWords.valid("abc"), "three letters is valid");
        expect(!CommandWords.valid("ab"), "two letters is too short");
        expect(CommandWords.valid("abcdefghijklmnop"), "sixteen letters is valid");
        expect(!CommandWords.valid("abcdefghijklmnopq"), "seventeen letters is too long");
        expect(CommandWords.valid("  Vaelrix  "), "trimmed and case-folded");
        expect(!CommandWords.valid("dra gon"), "no spaces inside");
        expect(!CommandWords.valid("drag0n"), "no digits");
        expect(!CommandWords.valid("dra-gon"), "no dashes");
        expect(!CommandWords.valid("drägon"), "no accents");
        expect(!CommandWords.valid(null), "null is not a word");
        expect(!CommandWords.valid(""), "empty is not a word");
        expect(!CommandWords.valid("   "), "blank is not a word");
        for (String reserved : CommandWords.RESERVED) {
            expect(!CommandWords.valid(reserved), "reserved word refused: " + reserved);
            expect(!CommandWords.valid(reserved.toUpperCase()), "reserved word refused in capitals: " + reserved);
        }
        expect("vaelrix".equals(CommandWords.normalize("  VaElRiX ")), "normalize");
        expect(CommandWords.normalize(null) == null, "normalize null");
        Set<String> seen = new HashSet<>();
        for (String d : CommandWords.DEFAULTS) {
            expect(CommandWords.valid(d), "default is valid: " + d);
            expect(seen.add(d), "default is unique: " + d);
        }
        expect(CommandWords.DEFAULTS.size() == 20, "twenty defaults");

        Random r = new Random(9);
        Set<String> picked = new HashSet<>();
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String w = CommandWords.pickDefault(taken, r);
            expect(CommandWords.valid(w), "picked word valid: " + w);
            expect(taken.add(w), "picked word not already taken: " + w);
            expect(CommandWords.DEFAULTS.contains(w), "first twenty come from the defaults: " + w);
            picked.add(w);
        }
        expect(picked.size() == 20, "twenty distinct picks");
        for (int i = 0; i < 40; i++) {
            String w = CommandWords.pickDefault(taken, r);
            expect(CommandWords.valid(w), "overflow word valid: " + w);
            expect(w.length() <= CommandWords.MAX_LENGTH, "overflow word within length: " + w);
            expect(taken.add(w), "overflow word is new: " + w);
        }
        Set<String> onlyOne = new HashSet<>(CommandWords.DEFAULTS);
        onlyOne.remove("nyxara");
        expect("nyxara".equals(CommandWords.pickDefault(onlyOne, r)), "the one free default is the one chosen");
        // a long default with letters appended must fall back rather than exceed 16
        Set<String> every = new HashSet<>(CommandWords.DEFAULTS);
        for (int i = 0; i < 200; i++) {
            String w = CommandWords.pickDefault(every, new Random(i));
            expect(w.length() >= CommandWords.MIN_LENGTH && w.length() <= CommandWords.MAX_LENGTH && !every.contains(w), "overflow pick " + i);
            every.add(w);
        }
        // exactly 16 letters is allowed: a "random" that always answers 0 walks vaelrix -> vaelrixa -> ... and stops at 16
        Set<String> crowded = new HashSet<>(CommandWords.DEFAULTS);
        for (int k = 1; k <= 8; k++) {
            crowded.add("vaelrix" + "a".repeat(k));
        }
        Random zero = new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        String sixteen = CommandWords.pickDefault(crowded, zero);
        expect(("vaelrix" + "a".repeat(9)).equals(sixteen) && sixteen.length() == 16, "a 16 letter word is accepted as is: " + sixteen);
    }

    static Targeting.Candidate c(String name, double d, boolean owner, boolean ally, boolean hurtable) {
        return new Targeting.Candidate(UUID.nameUUIDFromBytes(name.getBytes()), name, d, owner, ally, hurtable);
    }

    static void targeting() {
        List<Targeting.Candidate> all = new ArrayList<>();
        Targeting.Result none = Targeting.pick(all, Targeting.Mode.NEAREST, null, 64);
        expect(!none.ok() && none.reason() == Targeting.Reason.NOBODY_IN_RANGE && none.targets().isEmpty(), "nobody at all");

        all.add(c("Owner", 3, true, false, true));
        all.add(c("Friend", 4, false, true, true));
        all.add(c("Ghost", 5, false, false, false));
        all.add(c("Far", 80, false, false, true));
        Targeting.Result onlyBad = Targeting.pick(all, Targeting.Mode.NEAREST, null, 64);
        expect(onlyBad.reason() == Targeting.Reason.NOBODY_IN_RANGE, "owner, ally, unhurtable and far are never picked");
        expect(Targeting.pick(all, Targeting.Mode.ALL, null, 64).reason() == Targeting.Reason.NOBODY_IN_RANGE, "all: same");

        all.add(c("Near", 10, false, false, true));
        all.add(c("Mid", 20, false, false, true));
        all.add(c("Edge", 64, false, false, true));
        Targeting.Result nearest = Targeting.pick(all, Targeting.Mode.NEAREST, "ignored", 64);
        expect(nearest.ok() && nearest.targets().size() == 1 && nearest.targets().get(0).name().equals("Near"), "nearest is Near");
        Targeting.Result everyone = Targeting.pick(all, Targeting.Mode.ALL, null, 64);
        expect(everyone.ok() && everyone.targets().size() == 3, "all picks the three valid ones (" + everyone.targets().size() + ")");
        expect(everyone.targets().get(0).name().equals("Near") && everyone.targets().get(1).name().equals("Mid")
                && everyone.targets().get(2).name().equals("Edge"), "all is ordered nearest first");
        expect(Targeting.pick(all, Targeting.Mode.ALL, null, 63.9).targets().size() == 2, "exactly at the range is in, beyond is out");
        expect(Targeting.pick(all, Targeting.Mode.ALL, null, 64).targets().size() == 3, "range edge inclusive");

        // named
        Targeting.Result named = Targeting.pick(all, Targeting.Mode.NAMED, "mid", 64);
        expect(named.ok() && named.targets().size() == 1 && named.targets().get(0).name().equals("Mid"), "named ignores case");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "  MID ", 64).ok(), "named trims");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "Nobody", 64).reason() == Targeting.Reason.NOT_FOUND, "unknown name");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, null, 64).reason() == Targeting.Reason.NOT_FOUND, "no name given");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "Owner", 64).reason() == Targeting.Reason.OWNER, "named owner refused");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "Friend", 64).reason() == Targeting.Reason.ALLY, "named ally refused");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "Ghost", 64).reason() == Targeting.Reason.CANNOT_BE_HURT, "named creative player refused");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "Far", 64).reason() == Targeting.Reason.TOO_FAR, "named far player refused");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "Edge", 64).ok(), "named exactly at range is fine");
        expect(Targeting.pick(all, Targeting.Mode.NAMED, "Edge", 63).reason() == Targeting.Reason.TOO_FAR, "named just past range");
        // an owner who is also an ally is still reported as the owner
        List<Targeting.Candidate> both = List.of(c("Me", 1, true, true, true));
        expect(Targeting.pick(both, Targeting.Mode.NAMED, "me", 64).reason() == Targeting.Reason.OWNER, "owner outranks ally");
        // not hurtable AND ally: ally reported first
        List<Targeting.Candidate> allyGhost = List.of(c("Pal", 1, false, true, false));
        expect(Targeting.pick(allyGhost, Targeting.Mode.NAMED, "pal", 64).reason() == Targeting.Reason.ALLY, "ally outranks cannot-be-hurt");
        // two with the same distance keep a stable result
        List<Targeting.Candidate> tie = List.of(c("A", 5, false, false, true), c("B", 5, false, false, true));
        expect(Targeting.pick(tie, Targeting.Mode.NEAREST, null, 64).targets().get(0).name().equals("A"), "ties keep the first");
        expect(new Targeting.Result(Targeting.Reason.OK, List.of()).ok(), "ok() is true for OK");
        expect(!new Targeting.Result(Targeting.Reason.OWNER, List.of()).ok(), "ok() is false for others");
    }
}
