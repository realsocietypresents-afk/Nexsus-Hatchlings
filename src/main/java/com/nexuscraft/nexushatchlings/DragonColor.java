package com.nexuscraft.nexushatchlings;

import java.util.Locale;
import java.util.Random;

/**
 * The sixteen colours a dragon can be. A vanilla Ender Dragon has one fixed look, so the colour shows as the glowing
 * outline (a scoreboard team colour), the name tag and a trail of coloured dust. Every colour matches one of the sixteen
 * vanilla dyes, so using a dye on a dragon recolours it. Pure data, no server classes.
 */
public enum DragonColor {
    CRIMSON("crimson", "Crimson", "RED_DYE", "RED", 'c', 0xC8, 0x1E, 0x1E),
    AMBER("amber", "Amber", "ORANGE_DYE", "GOLD", '6', 0xF0, 0x8A, 0x1C),
    SUNLIT("sunlit", "Sunlit", "YELLOW_DYE", "YELLOW", 'e', 0xF5, 0xD5, 0x2B),
    LIME("lime", "Lime", "LIME_DYE", "GREEN", 'a', 0x7C, 0xD6, 0x2A),
    FOREST("forest", "Forest", "GREEN_DYE", "DARK_GREEN", '2', 0x2E, 0x7D, 0x32),
    TEAL("teal", "Teal", "CYAN_DYE", "DARK_AQUA", '3', 0x1F, 0x9E, 0xA8),
    SKY("sky", "Sky", "LIGHT_BLUE_DYE", "AQUA", 'b', 0x5C, 0xC8, 0xF0),
    SAPPHIRE("sapphire", "Sapphire", "BLUE_DYE", "BLUE", '9', 0x2A, 0x4B, 0xD6),
    VIOLET("violet", "Violet", "PURPLE_DYE", "DARK_PURPLE", '5', 0x7A, 0x2E, 0xB0),
    MAGENTA("magenta", "Magenta", "MAGENTA_DYE", "LIGHT_PURPLE", 'd', 0xC6, 0x3F, 0xC6),
    ROSE("rose", "Rose", "PINK_DYE", "LIGHT_PURPLE", 'd', 0xF2, 0x8C, 0xB0),
    IVORY("ivory", "Ivory", "WHITE_DYE", "WHITE", 'f', 0xF2, 0xF2, 0xEA),
    ASH("ash", "Ash", "LIGHT_GRAY_DYE", "GRAY", '7', 0xA0, 0xA0, 0xA0),
    SLATE("slate", "Slate", "GRAY_DYE", "DARK_GRAY", '8', 0x50, 0x55, 0x5C),
    ONYX("onyx", "Onyx", "BLACK_DYE", "BLACK", '0', 0x18, 0x18, 0x1C),
    RUST("rust", "Rust", "BROWN_DYE", "DARK_RED", '4', 0x8A, 0x4B, 0x2B);

    private final String id;
    private final String display;
    private final String dye;
    private final String teamColor;
    private final char chatCode;
    private final int red;
    private final int green;
    private final int blue;

    DragonColor(String id, String display, String dye, String teamColor, char chatCode, int red, int green, int blue) {
        this.id = id;
        this.display = display;
        this.dye = dye;
        this.teamColor = teamColor;
        this.chatCode = chatCode;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    /** The Material name of the dye that makes a dragon this colour. */
    public String dye() {
        return dye;
    }

    /** The name of the vanilla chat/team colour closest to this one (a ChatColor constant). */
    public String teamColor() {
        return teamColor;
    }

    /** The legacy colour code letter for name tags, without the section sign. */
    public char chatCode() {
        return chatCode;
    }

    public int red() {
        return red;
    }

    public int green() {
        return green;
    }

    public int blue() {
        return blue;
    }

    /** The colour with this id (case does not matter), or null. */
    public static DragonColor byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (DragonColor c : values()) {
            if (c.id.equals(wanted)) {
                return c;
            }
        }
        return null;
    }

    /** The colour a dye makes, given the dye's Material name (case does not matter), or null if it is not a dye. */
    public static DragonColor byDye(String materialName) {
        if (materialName == null) {
            return null;
        }
        String wanted = materialName.trim().toUpperCase(Locale.ROOT);
        for (DragonColor c : values()) {
            if (c.dye.equals(wanted)) {
                return c;
            }
        }
        return null;
    }

    /** Any colour, evenly. */
    public static DragonColor random(Random random) {
        DragonColor[] all = values();
        return all[random.nextInt(all.length)];
    }
}
