package com.nexuscraft.nexushatchlings;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;

import java.util.List;

import static com.nexuscraft.nexushatchlings.Kit.expect;

/** /dragon: every sub-command and the word shorthand, and what the tab key offers. */
public class CommandTest {

    public static void main(String[] args) {
        help();
        listing();
        words();
        attacking();
        stopping();
        colouring();
        eggs();
        completing();
        Kit.finish();
    }

    static final Command CMD = new Command();

    static boolean run(DragonCommand c, CommandSender who, String... args) {
        boolean handled = c.onCommand(who, CMD, "dragon", args);
        // returning false would make the server print the usage text on top of our own message
        expect(handled, "/dragon " + String.join(" ", args) + " is always handled (returns true)");
        return handled;
    }

    static DragonCommand command(Env e) {
        return new DragonCommand(e.svc, e.eggs);
    }

    static Kit.TestPlayer staff(Env e, String name, double x, double z) {
        Kit.TestPlayer p = e.player(name, x, 64, z);
        p.perms.add("nexushatchlings.admin");
        return p;
    }

    static void help() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        expect(run(c, p), "no arguments handled");
        expect(p.heard("Dragons") && p.heard("/dragon <word>") && p.heard("/dragon <word> <player>") && p.heard("/dragon <word> all"), "help shows the three ways to attack: " + p.messages);
        expect(p.heard("Hatchling Crystal") && p.heard("campfire"), "and how to get and tame one");
        expect(!p.heard("/dragon color") && !p.heard("/dragon egg"), "players are not shown the staff commands");
        expect(p.heard("dye") && p.heard("climb on") && p.heard("sneak") && p.heard("fully grown"), "and how colours, riding and fighting work: " + p.messages);
        expect(p.heard("nearest player") && p.heard("/dragon stop") && p.heard("/dragon list") && p.heard("/dragon word") && p.heard("never attacked"),
                "and the other commands: " + p.messages);
        expect(p.heard("/dragon menu") && p.heard("/dragon stay") && p.heard("/dragon follow") && p.heard("/dragon release") && p.heard("ten by default"),
                "and the menu, stay, follow, release and the limit: " + p.messages);
        expect(DragonText.help(false).size() == 19 && DragonText.help(true).size() == 21, "19 lines for players, 2 more for staff: "
                + DragonText.help(false).size() + "/" + DragonText.help(true).size());
        expect(DragonText.help(false).get(0).equals(DragonText.help(false).get(18)), "the guide is framed top and bottom");
        expect(DragonText.help(false).get(5).trim().isEmpty(), "with a blank line before the commands");
        Kit.TestPlayer s = staff(e, "S", 0, 0);
        run(c, s, "help");
        expect(s.heard("/dragon color") && s.heard("/dragon egg"), "staff are");
        Kit.Console console = new Kit.Console();
        run(c, console, "HELP");
        expect(console.said("Dragons"), "the console can read it, and the word is not case sensitive");
    }

    static void listing() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.Console console = new Kit.Console();
        run(c, console, "list");
        expect(console.said("Run this in game"), "the console has no dragons");
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        run(c, p, "list");
        expect(p.heard("no dragons loaded") && p.heard("campfire"), "nothing to list: " + p.messages);
        EnderDragon d = e.fledgling(p, 3, 64, 0);
        e.svc.setWord(d, p.getUniqueId(), "zorgath");
        EnderDragon d2 = e.adult(p, 6, 64, 0);
        e.svc.setWord(d2, p.getUniqueId(), "kaelthi");
        p.messages.clear();
        run(c, p, "list");
        expect(p.messages.size() == 3, "a heading and a line each (" + p.messages.size() + ")");
        expect(p.heard("word zorgath") && p.heard("word kaelthi") && p.heard("fledgling") && p.heard("adult"), "with word and stage: " + p.messages);
        p.messages.clear();
        run(c, p, "info");
        expect(p.messages.size() == 3, "info is the same");
        Kit.TestPlayer other = e.player("Other", 0, 64, 0);
        run(c, other, "list");
        expect(other.heard("no dragons loaded"), "someone else's dragons are not shown");
    }

    static void words() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        EnderDragon d = e.fledgling(p, 3, 64, 0);
        EnderDragon d2 = e.fledgling(p, 4, 64, 0);
        e.svc.setWord(d, p.getUniqueId(), "zorgath");
        e.svc.setWord(d2, p.getUniqueId(), "kaelthi");
        run(c, p, "word");
        expect(p.heard("Usage: /dragon word"), "usage: " + p.messages);
        p.messages.clear();
        run(c, p, "word", "zorgath");
        expect(p.heard("Usage: /dragon word"), "needs both words");
        p.messages.clear();
        run(c, p, "word", "nosuch", "fine");
        expect(p.heard("no dragon with the word \"nosuch\""), "unknown word: " + p.messages);
        p.messages.clear();
        run(c, p, "word", "zorgath", "Embergale");
        expect(p.heard("answers to") && p.heard("embergale"), "renamed: " + p.messages);
        expect("embergale".equals(e.svc.wordOf(d)), "and it stuck");
        p.messages.clear();
        run(c, p, "word", "embergale", "kaelthi");
        expect(p.heard("already has that word"), "taken: " + p.messages);
        p.messages.clear();
        run(c, p, "word", "embergale", "ab");
        expect(p.heard("3 to 16 letters") && p.heard("attack") && p.heard("all"), "invalid shows the rules and the reserved words: " + p.messages);
        Kit.Console console = new Kit.Console();
        run(c, console, "word", "a", "b");
        expect(console.said("Run this in game"), "console cannot rename");
    }

    static void attacking() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        Kit.TestPlayer prey = e.player("Prey", 20, 64, 0);
        Kit.TestPlayer prey2 = e.player("Prey2", 10, 64, 5);
        EnderDragon d = e.adult(p, 3, 64, 0);
        e.svc.setWord(d, p.getUniqueId(), "zorgath");
        run(c, p, "attack");
        expect(p.heard("Usage: /dragon attack"), "usage: " + p.messages);
        p.messages.clear();
        run(c, p, "attack", "nosuch");
        expect(p.heard("no dragon with the word \"nosuch\"") && p.heard("/dragon list"), "unknown word points to the list: " + p.messages);
        // the word on its own: nearest
        p.messages.clear();
        run(c, p, "zorgath");
        expect(e.svc.isAttacking(d), "the word alone sends it");
        expect(p.heard("goes after Prey2"), "at the nearest player: " + p.messages);
        e.svc.stop(d);
        // the word and a player
        p.messages.clear();
        run(c, p, "zorgath", "prey");
        expect(p.heard("goes after Prey") && !p.heard("Prey2"), "the word plus a name: " + p.messages);
        e.svc.stop(d);
        // the word and all
        p.messages.clear();
        run(c, p, "zorgath", "all");
        expect(p.heard("Prey2 and Prey") || p.heard("Prey and Prey2"), "all: " + p.messages);
        e.svc.stop(d);
        // the long form
        p.messages.clear();
        run(c, p, "attack", "ZORGATH", "Prey");
        expect(e.svc.isAttacking(d) && p.heard("goes after Prey") && !p.heard("Prey2"), "attack <word> <name> picks that player, not the nearest: " + p.messages);
        e.svc.stop(d);
        run(c, p, "attack", "zorgath", "all");
        expect(e.svc.isAttacking(d), "attack <word> all");
        e.svc.stop(d);
        run(c, p, "attack", "zorgath");
        expect(e.svc.isAttacking(d), "attack <word>");
        e.svc.stop(d);
        // all the ways it can fail, each with its own words
        p.messages.clear();
        run(c, p, "zorgath", "Ghosty");
        expect(p.heard("nobody called Ghosty close enough"), "unknown name: " + p.messages);
        p.messages.clear();
        run(c, p, "zorgath", "P");
        expect(p.heard("will not go after you"), "the owner by name: " + p.messages);
        e.houses.pals.add(p.getUniqueId());
        e.houses.pals.add(prey.getUniqueId());
        p.messages.clear();
        run(c, p, "zorgath", "Prey");
        expect(p.heard("Prey stands with you"), "an ally by name: " + p.messages);
        prey.setGameMode(org.bukkit.GameMode.CREATIVE);
        e.houses.pals.clear();
        p.messages.clear();
        run(c, p, "zorgath", "Prey");
        expect(p.heard("Prey cannot be hurt right now"), "a creative player by name: " + p.messages);
        prey.setGameMode(org.bukkit.GameMode.SURVIVAL);
        prey.place(e.world, 120, 64, 0, 0f, 0f);
        p.messages.clear();
        run(c, p, "zorgath", "Prey");
        expect(p.heard("Prey is too far from your dragon"), "a far player by name: " + p.messages);
        prey.place(e.world, 20, 64, 0, 0f, 0f);
        prey2.online = true;
        for (Kit.TestPlayer q : List.of(prey, prey2)) {
            q.place(e.world, 500, 64, 500, 0f, 0f);
        }
        p.messages.clear();
        run(c, p, "zorgath");
        expect(p.heard("nobody near your dragon"), "nobody around: " + p.messages);
        e.cfg.attackEnabled = false;
        p.messages.clear();
        run(c, p, "zorgath");
        expect(p.heard("not allowed to attack"), "attacks off: " + p.messages);
        e.cfg.attackEnabled = true;
        EnderDragon young = e.fledgling(p, 3, 64, 0);
        e.svc.setWord(young, p.getUniqueId(), "ilvarth");
        p.messages.clear();
        run(c, p, "ilvarth");
        expect(p.heard("not fully grown"), "too young: " + p.messages);
        e.svc.mount(p, d);
        p.messages.clear();
        run(c, p, "zorgath");
        expect(p.heard("carrying a rider"), "busy: " + p.messages);
        d.removePassenger(p);
        e.tick(1);
        p.place(e.world, 300, 64, 0, 0f, 0f);
        p.messages.clear();
        run(c, p, "zorgath");
        expect(p.heard("too far away to hear"), "out of earshot: " + p.messages);
        p.place(new org.bukkit.World("nether"), 0, 64, 0, 0f, 0f);
        p.messages.clear();
        run(c, p, "zorgath");
        expect(p.heard("another world"), "other world: " + p.messages);
        Kit.Console console = new Kit.Console();
        run(c, console, "zorgath");
        expect(console.said("Run this in game"), "console has no dragons");
        // a word that is also a sub-command word cannot exist, so the sub-commands always win
        Kit.TestPlayer q = e.player("Q", 0, 64, 0);
        run(c, q, "list");
        expect(q.heard("no dragons loaded"), "list is the list");
    }

    static void stopping() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        e.player("Prey", 20, 64, 0);
        EnderDragon a = e.adult(p, 3, 64, 0);
        EnderDragon b = e.adult(p, 4, 64, 0);
        e.svc.setWord(a, p.getUniqueId(), "zorgath");
        e.svc.setWord(b, p.getUniqueId(), "kaelthi");
        run(c, p, "stop");
        expect(p.heard("None of your dragons was fighting"), "nothing to stop: " + p.messages);
        run(c, p, "zorgath");
        run(c, p, "kaelthi");
        p.messages.clear();
        run(c, p, "stop", "zorgath");
        expect(!e.svc.isAttacking(a) && e.svc.isAttacking(b), "stop <word> calls off just that one");
        expect(p.heard("Called off 1"), p.messages.toString());
        p.messages.clear();
        run(c, p, "stop", "zorgath");
        expect(p.heard("None of your dragons was fighting"), "stopping the idle one: " + p.messages);
        run(c, p, "zorgath");
        p.messages.clear();
        run(c, p, "stop");
        expect(!e.svc.isAttacking(a) && !e.svc.isAttacking(b) && p.heard("Called off 2"), "stop alone calls off all: " + p.messages);
        p.messages.clear();
        run(c, p, "stop", "nosuch");
        expect(p.heard("no dragon with the word \"nosuch\""), "unknown: " + p.messages);
        Kit.Console console = new Kit.Console();
        run(c, console, "stop");
        expect(console.said("Run this in game"), "console");
    }

    static void colouring() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        run(c, p, "color", "sky");
        expect(p.heard("don't have permission") && p.heard("dye"), "players are pointed at dyes: " + p.messages);
        Kit.TestPlayer s = staff(e, "S", 0, 0);
        run(c, s, "color");
        expect(s.heard("Usage: /dragon color <") && s.heard("crimson") && s.heard("onyx"), "usage lists every colour: " + s.messages);
        String ids = DragonColor.values()[0].id() + ", " + DragonColor.values()[1].id() + ", ";
        expect(s.heard("<" + ids), "the colours are listed with commas between them and none in front: " + s.messages);
        s.messages.clear();
        run(c, s, "colour", "plaid");
        expect(s.heard("Usage"), "unknown colour: " + s.messages);
        s.messages.clear();
        run(c, s, "color", "sky");
        expect(s.heard("No dragon within 16 blocks"), "none near: " + s.messages);
        EnderDragon near = e.baby(5, 64, 0);
        EnderDragon nearer = e.baby(2, 64, 0);
        EnderDragon far = e.baby(40, 64, 0);
        e.svc.setColor(far, DragonColor.ONYX);
        s.messages.clear();
        run(c, s, "color", "SKY");
        expect(e.svc.colorOf(nearer) == DragonColor.SKY, "the nearest one changes");
        expect(e.svc.colorOf(far) == DragonColor.ONYX, "the far one is untouched");
        expect(s.heard("It is sky now"), s.messages.toString());
        // the nearest is measured to the staff member, not by list order
        s.place(e.world, 5, 64, 0, 0f, 0f);
        run(c, s, "colour", "rose");
        expect(e.svc.colorOf(near) == DragonColor.ROSE, "from where staff stand");
        Kit.Console console = new Kit.Console();
        console.allowed = true;
        run(c, console, "color", "sky");
        expect(console.said("Run this in game"), "console");
        // exactly 16 blocks away still counts, a little more does not
        Kit.TestPlayer edge = staff(e, "Edge", 100, 100);
        edge.place(e.world, 100, 64, 100, 0f, 0f);
        EnderDragon sixteen = e.baby(116, 64, 100);
        e.svc.setColor(sixteen, DragonColor.ONYX);
        run(c, edge, "color", "ivory");
        expect(e.svc.colorOf(sixteen) == DragonColor.IVORY, "a dragon exactly 16 blocks away is the nearest");
        edge.place(e.world, 83.5, 64, 100, 0f, 0f);
        edge.messages.clear();
        run(c, edge, "color", "rose");
        expect(edge.heard("No dragon within 16 blocks"), "16.5 blocks is too far: " + edge.messages);
        // another world does not count
        s.place(new org.bukkit.World("nether"), 0, 64, 0, 0f, 0f);
        s.messages.clear();
        run(c, s, "color", "lime");
        expect(s.heard("No dragon within 16 blocks"), "other world: " + s.messages);
    }

    static void eggs() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.TestPlayer p = e.player("P", 0, 64, 0);
        run(c, p, "egg", "give");
        expect(p.heard("don't have permission"), "players cannot: " + p.messages);
        Kit.TestPlayer s = staff(e, "S", 0, 0);
        run(c, s, "egg");
        expect(s.heard("Usage: /dragon egg"), "usage: " + s.messages);
        s.messages.clear();
        run(c, s, "egg", "bogus");
        expect(s.heard("Usage: /dragon egg"), "unknown: " + s.messages);
        s.messages.clear();
        run(c, s, "egg", "give");
        expect(s.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.STONE)).isEmpty(), "(inventory works)");
        boolean got = false;
        for (int i = 0; i < 36; i++) {
            org.bukkit.inventory.ItemStack it = s.getInventory().getItem(i);
            got |= it != null && it.getType() == Material.DRAGON_EGG;
        }
        expect(got, "staff give themselves an egg");
        expect(s.heard("Gave S a Dragon Egg"), s.messages.toString());
        run(c, s, "egg", "give", "P");
        boolean theirs = false;
        for (int i = 0; i < 36; i++) {
            org.bukkit.inventory.ItemStack it = p.getInventory().getItem(i);
            theirs |= it != null && it.getType() == Material.DRAGON_EGG;
        }
        expect(theirs, "or someone else");
        s.messages.clear();
        run(c, s, "egg", "give", "Nobody");
        expect(s.heard("Usage: /dragon egg give"), "an offline name: " + s.messages);
        Kit.Console console = new Kit.Console();
        run(c, console, "egg", "give");
        expect(console.said("Usage: /dragon egg give"), "the console must name a player");
        run(c, console, "egg", "give", "P");
        expect(console.said("Gave P a Dragon Egg"), "and can give to one");
        // list, spawn, hatch
        s.messages.clear();
        run(c, s, "egg", "list");
        expect(s.heard("0 egg(s) tracked, 0 dragon(s) loaded"), s.messages.toString());
        s.messages.clear();
        run(c, s, "egg", "spawn");
        expect(e.svc.loaded().size() == 1 && s.heard("wild baby dragon"), "spawn makes a baby: " + s.messages);
        run(c, console, "egg", "spawn");
        expect(console.said("Run this in game"), "the console cannot spawn");
        s.messages.clear();
        run(c, s, "egg", "hatch");
        expect(s.heard("No tracked egg within 10 blocks"), "nothing to hatch: " + s.messages);
        run(c, console, "egg", "hatch");
        expect(console.said("Run this in game"), "the console cannot hatch");
        e.world.getBlockAt(0, 63, 3).setType(Material.CAMPFIRE);
        Block egg = e.world.getBlockAt(0, 64, 3);
        egg.setType(Material.DRAGON_EGG);
        e.eggs.register(egg);
        run(c, s, "egg", "hatch");
        expect(egg.getType() == Material.AIR && e.svc.loaded().size() == 2, "hatch pops the nearest egg");
        run(c, s, "egg", "list");
        expect(s.heard("0 egg(s) tracked, 2 dragon(s) loaded"), "and the list agrees: " + s.messages);
    }

    static void completing() {
        Env e = new Env();
        DragonCommand c = command(e);
        Kit.TestPlayer p = e.player("Pip", 0, 64, 0);
        Kit.TestPlayer q = e.player("Quill", 0, 64, 0);
        EnderDragon d = e.adult(p, 3, 64, 0);
        e.svc.setWord(d, p.getUniqueId(), "zorgath");
        EnderDragon d2 = e.adult(q, 4, 64, 0);
        e.svc.setWord(d2, q.getUniqueId(), "quillfire");
        Command cmd = CMD;
        List<String> first = c.onTabComplete(p, cmd, "dragon", new String[]{""});
        expect(first.containsAll(List.of("help", "list", "word", "attack", "stop", "color", "egg", "zorgath")), "first word: sub-commands and your own words: " + first);
        expect(!first.contains("quillfire"), "not other people's words");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"zo"}).equals(List.of("zorgath")), "filtered by what is typed");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"ZO"}).equals(List.of("zorgath")), "ignoring case");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"zzz"}).isEmpty(), "nothing matches");
        List<String> word2 = c.onTabComplete(p, cmd, "dragon", new String[]{"attack", ""});
        expect(word2.equals(List.of("zorgath")), "attack <word>: your words: " + word2);
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"stop", ""}).equals(List.of("zorgath")), "stop <word>");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"word", ""}).equals(List.of("zorgath")), "word <word>");
        List<String> colours = c.onTabComplete(p, cmd, "dragon", new String[]{"color", ""});
        expect(colours.size() == 16 && colours.contains("sapphire"), "color <colour>: all sixteen");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"colour", "ro"}).equals(List.of("rose")) , "colour spelled the other way, filtered: "
                + c.onTabComplete(p, cmd, "dragon", new String[]{"colour", "ro"}));
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"egg", ""}).equals(List.of("give", "hatch", "spawn", "list")), "egg sub-commands");
        List<String> targets = c.onTabComplete(p, cmd, "dragon", new String[]{"zorgath", ""});
        expect(targets.contains("all") && targets.contains("Pip") && targets.contains("Quill"), "word <target>: all and the players: " + targets);
        List<String> longTargets = c.onTabComplete(p, cmd, "dragon", new String[]{"attack", "zorgath", ""});
        expect(longTargets.contains("all") && longTargets.contains("Quill"), "attack <word> <target>");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"attack", "zorgath", "qu"}).equals(List.of("Quill")), "filtered players");
        List<String> give = c.onTabComplete(p, cmd, "dragon", new String[]{"egg", "give", ""});
        expect(give.contains("Pip") && give.contains("Quill") && !give.contains("all"), "egg give <player>");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"egg", "hatch", ""}).isEmpty(), "no players after egg hatch");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"word", "give", ""}).isEmpty(), "no players after word <word>");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"stop", "zorgath", ""}).isEmpty(), "nothing further after stop <word>");
        expect(c.onTabComplete(p, cmd, "dragon", new String[]{"zorgath", "Pip", "x"}).isEmpty(), "nothing after the target");
        Kit.Console console = new Kit.Console();
        List<String> consoleFirst = c.onTabComplete(console, cmd, "dragon", new String[]{""});
        expect(consoleFirst.contains("help") && !consoleFirst.contains("zorgath"), "the console sees sub-commands only");
        expect(c.onTabComplete(p, cmd, "dragon", new String[0]).containsAll(List.of("help", "list")), "no arguments at all");
    }
}
