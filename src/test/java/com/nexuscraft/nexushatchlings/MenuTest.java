package com.nexuscraft.nexushatchlings;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.nexuscraft.nexushatchlings.Kit.expect;

/** The dragon chest menu and the chat prompt behind "change command word". */
public class MenuTest {

    public static void main(String[] args) {
        listing();
        crowded();
        detail();
        orders();
        colours();
        words();
        releasing();
        safety();
        prompts();
        Kit.finish();
    }

    // ------------------------------------------------------------------ helpers

    static final class Rig {
        final Env e = new Env();
        final ChatPrompt prompt = new ChatPrompt(e.plugin);
        final DragonMenu menu = new DragonMenu(e.svc, prompt);
        final Kit.TestPlayer p;

        Rig() {
            e.svc.setRoster(new DragonRoster(e.dir, e.warnings::add, e.clock::get));
            p = e.player("Owner", 0, 64, 0);
        }

        DragonMenu.Holder open() {
            menu.open(p);
            return holder();
        }

        DragonMenu.Holder holder() {
            return (DragonMenu.Holder) p.stubOpened.getHolder();
        }

        /** A left click on a slot of the menu. */
        InventoryClickEvent click(int slot) {
            return click(p, holder().getInventory(), slot, InventoryType.SlotType.CONTAINER);
        }

        InventoryClickEvent click(Kit.TestPlayer who, Inventory top, int slot, InventoryType.SlotType type) {
            InventoryView view = new InventoryView();
            view.setTopInventory(top);
            view.setBottomInventory(who.getInventory());
            view.setPlayer(who);
            InventoryClickEvent event = new InventoryClickEvent(view, type, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            menu.onClick(event);
            return event;
        }

        /** The slot of the button for this dragon on the list page. */
        int slotOf(DragonMenu.Holder h, String word) {
            for (int slot : DragonMenu.DRAGON_SLOTS) {
                ItemStack it = h.getInventory().getItem(slot);
                if (it != null && it.getItemMeta().getDisplayName().replaceAll("§.", "").equalsIgnoreCase(word)) {
                    return slot;
                }
            }
            return -1;
        }
    }

    static String name(Inventory inv, int slot) {
        ItemStack it = inv.getItem(slot);
        return it == null ? null : it.getItemMeta().getDisplayName().replaceAll("§.", "");
    }

    static String lore(Inventory inv, int slot) {
        ItemStack it = inv.getItem(slot);
        if (it == null || it.getItemMeta().getLore() == null) {
            return "";
        }
        return String.join("\n", it.getItemMeta().getLore()).replaceAll("§.", "");
    }

    static int filled(Inventory inv) {
        int n = 0;
        for (int i = 0; i < DragonMenu.SIZE; i++) {
            if (inv.getItem(i) != null) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ the list

    static void listing() {
        Rig r = new Rig();
        DragonMenu.Holder empty = r.open();
        expect(empty.getInventory() != null && empty.getInventory().getHolder() == empty, "the chest knows its holder");
        expect(empty.page == DragonMenu.Page.LIST && r.p.stubOpens == 1, "opens on the list");
        expect(name(empty.getInventory(), 22).equals("No dragons near enough"), "an empty list says so");
        expect(lore(empty.getInventory(), 4).contains("0") && lore(empty.getInventory(), 4).contains("10"), "and shows 0 of 10: " + lore(empty.getInventory(), 4));

        EnderDragon a = r.e.adult(r.p, 3, 64, 0);
        EnderDragon b = r.e.fledgling(r.p, 3, 64, 0);
        EnderDragon c = r.e.tame(r.e.baby(3, 64, 0), r.p, 0.0);
        Kit.TestPlayer other = r.e.player("Other", 50, 64, 0);
        EnderDragon notMine = r.e.adult(other, 3, 64, 0);
        // all three of mine stand on exactly the same spot, yet each has a button of its own
        DragonMenu.Holder h = r.open();
        Set<Integer> slots = new HashSet<>();
        for (EnderDragon d : List.of(a, b, c)) {
            int slot = r.slotOf(h, r.e.svc.wordOf(d));
            expect(slot >= 0, "a button for " + r.e.svc.wordOf(d));
            expect(slots.add(slot), "each on a slot of its own");
            ItemStack icon = h.getInventory().getItem(slot);
            expect(icon.getType().name().endsWith("_DYE"), "shown as the dye of its colour: " + icon.getType());
            expect(icon.getType() == Material.matchMaterial(r.e.svc.colorOf(d).dye()), "the right dye");
            String l = lore(h.getInventory(), slot);
            expect(l.contains(r.e.svc.stageOf(d).display()) && l.contains("% grown") && l.contains("Following") && l.contains(r.e.svc.wordOf(d))
                    && l.contains(r.e.svc.colorOf(d).display()) && l.contains("Click for orders"), "its facts: " + l);
        }
        expect(r.slotOf(h, r.e.svc.wordOf(notMine)) < 0, "another player's dragon is not listed");
        expect(slots.size() == 3, "three buttons");
        expect(h.getInventory().getItem(22) == null, "no 'no dragons' marker when there are some");
        expect(lore(h.getInventory(), 4).contains("3") && lore(h.getInventory(), 4).contains("10"), "3 of 10 at the top: " + lore(h.getInventory(), 4));
        expect(name(h.getInventory(), 28).equals("All follow me") && name(h.getInventory(), 30).equals("All stay where they are")
                && name(h.getInventory(), 32).equals("All stay at my spot") && name(h.getInventory(), 34).equals("Close"), "the group buttons");
        for (int slot : DragonMenu.DRAGON_SLOTS) {
            expect(slot >= 0 && slot < DragonMenu.SIZE, "slots fit the chest");
        }

        // a staying dragon says where
        r.e.svc.stay(r.p, a, false);
        DragonMenu.Holder h2 = r.open();
        String l = lore(h2.getInventory(), r.slotOf(h2, r.e.svc.wordOf(a)));
        expect(l.contains("Staying") && l.contains("Waiting at: 3, 64, 0"), "a waiting dragon shows its spot: " + l);

        // sleeping ones are counted though not shown
        r.e.svc.roster().add(r.p.getUniqueId(), java.util.UUID.randomUUID());
        DragonMenu.Holder h3 = r.open();
        expect(lore(h3.getInventory(), 4).contains("1 more asleep"), "dragons too far away are mentioned: " + lore(h3.getInventory(), 4));
    }

    static void crowded() {
        Rig r = new Rig();
        r.e.cfg.maxPerPlayer = 20;
        List<EnderDragon> pack = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pack.add(r.e.adult(r.p, 0, 64, 0));
        }
        DragonMenu.Holder h = r.open();
        Set<Integer> slots = new HashSet<>();
        int buttons = 0;
        for (int slot : DragonMenu.DRAGON_SLOTS) {
            if (h.getInventory().getItem(slot) != null) {
                buttons++;
                slots.add(slot);
            }
        }
        expect(buttons == 10 && slots.size() == 10, "ten dragons on one spot: ten separate buttons");
        Set<String> names = new HashSet<>();
        for (int slot : slots) {
            names.add(name(h.getInventory(), slot));
        }
        expect(names.size() == 10, "each with its own name: " + names);
        // the buttons keep the same order from one opening to the next
        List<String> first = new ArrayList<>();
        for (int slot : DragonMenu.DRAGON_SLOTS) {
            first.add(name(h.getInventory(), slot));
        }
        DragonMenu.Holder again = r.open();
        List<String> second = new ArrayList<>();
        for (int slot : DragonMenu.DRAGON_SLOTS) {
            second.add(name(again.getInventory(), slot));
        }
        expect(first.equals(second), "and in the same order every time");
        // more than fit: the extra ones are left off, not crashed on
        for (int i = 0; i < 6; i++) {
            r.e.adult(r.p, 0, 64, 0);
        }
        DragonMenu.Holder many = r.open();
        int shown = 0;
        for (int slot : DragonMenu.DRAGON_SLOTS) {
            if (many.getInventory().getItem(slot) != null) {
                shown++;
            }
        }
        expect(shown == DragonMenu.DRAGON_SLOTS.length, "sixteen dragons fill every button slot (" + shown + ")");
        expect(lore(many.getInventory(), 4).contains("16"), "and the header still counts all of them");
    }

    // ------------------------------------------------------------------ one dragon's page

    static void detail() {
        Rig r = new Rig();
        EnderDragon d = r.e.adult(r.p, 3, 64, 0);
        EnderDragon spare = r.e.adult(r.p, 3, 64, 0);
        String word = r.e.svc.wordOf(d);
        DragonMenu.Holder h = r.open();
        int slot = r.slotOf(h, word);
        InventoryClickEvent ev = r.click(slot);
        expect(ev.isCancelled(), "nothing can be taken from the menu");
        expect(h.page == DragonMenu.Page.DETAIL && d.getUniqueId().equals(h.dragon), "clicking a dragon opens its page");
        expect(r.p.stubOpens == 1, "in the same chest, not a new one");
        expect(name(h.getInventory(), 4).equalsIgnoreCase(word), "the dragon is shown at the top: " + name(h.getInventory(), 4));
        String[] expected = {"Follow me", "Stay where it is", "Stay at my spot", null, "Attack the nearest player", "Call off the attack"};
        int[] at = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < at.length; i++) {
            if (expected[i] != null) {
                expect(expected[i].equals(name(h.getInventory(), at[i])), "button " + at[i] + " is " + expected[i] + " (was " + name(h.getInventory(), at[i]) + ")");
            }
        }
        expect("Change colour".equals(name(h.getInventory(), 19)) && "Change command word".equals(name(h.getInventory(), 20))
                && "Let it go".equals(name(h.getInventory(), 25)) && "Back to all dragons".equals(name(h.getInventory(), 31)), "the rest of the buttons");
        expect(!h.actions.containsKey(21) && h.getInventory().getItem(21) == null, "the empty slots do nothing");
        // back
        r.click(31);
        expect(h.page == DragonMenu.Page.LIST && h.dragon == null, "back returns to the list");
        expect(r.slotOf(h, word) >= 0 && r.slotOf(h, r.e.svc.wordOf(spare)) >= 0, "and both dragons are listed again");

        // a fledgling cannot fight: the button says so
        EnderDragon teen = r.e.fledgling(r.p, 3, 64, 0);
        DragonMenu.Holder h2 = r.open();
        r.click(r.slotOf(h2, r.e.svc.wordOf(teen)));
        expect(name(h2.getInventory(), 14).startsWith("Attack (not fully grown"), "a young dragon's attack button is greyed: " + name(h2.getInventory(), 14));
        expect(h2.getInventory().getItem(14).getType() == Material.BARRIER, "with a barrier");
    }

    static void orders() {
        Rig r = new Rig();
        Kit.TestPlayer foe = r.e.player("Foe", 20, 64, 0);
        foe.setGameMode(GameMode.SURVIVAL);
        EnderDragon d = r.e.adult(r.p, 5, 64, 0);
        String word = r.e.svc.wordOf(d);
        DragonMenu.Holder h = r.open();
        r.click(r.slotOf(h, word));

        // stay where it is
        r.click(11);
        expect(r.e.svc.isStaying(d), "stay where it is");
        expect(r.p.heard("waits right where it is"), "with a message: " + r.p.messages);
        expect(lore(h.getInventory(), 4).contains("Staying"), "and the page shows it straight away: " + lore(h.getInventory(), 4));
        // follow me
        r.click(10);
        expect(!r.e.svc.isStaying(d) && r.p.heard("follows you again"), "follow me");
        r.p.messages.clear();
        r.click(10);
        expect(r.p.heard("already following"), "following twice: " + r.p.messages);
        // stay at my spot
        r.p.place(r.e.world, 12, 64, 12, 0f, 0f);
        r.click(12);
        expect(r.e.svc.isStaying(d) && Math.abs(r.e.svc.stayOf(d).x() - 12.0) < 1e-9, "stay at my spot");
        // attack
        r.click(10);
        r.p.messages.clear();
        r.click(14);
        expect(r.e.svc.isAttacking(d) && r.p.heard("roars"), "attack the nearest: " + r.p.messages);
        expect(lore(h.getInventory(), 4).contains("Attacking"), "and the page shows it");
        r.click(15);
        expect(!r.e.svc.isAttacking(d) && r.p.heard("backs off"), "call off: " + r.p.messages);
        r.p.messages.clear();
        r.click(15);
        expect(r.p.heard("not fighting"), "calling off a peaceful dragon: " + r.p.messages);
        // nobody to attack
        foe.place(r.e.world, 500, 64, 0, 0f, 0f);
        r.p.messages.clear();
        r.click(14);
        expect(!r.e.svc.isAttacking(d) && r.p.heard("nobody"), "no one near: " + r.p.messages);
        // a young dragon
        EnderDragon teen = r.e.fledgling(r.p, 5, 64, 0);
        h.page = DragonMenu.Page.DETAIL;
        h.dragon = teen.getUniqueId();
        r.menu.render(r.p, h);
        r.p.messages.clear();
        r.click(14);
        expect(r.p.heard("not fully grown"), "a young dragon is refused: " + r.p.messages);

        // group buttons
        h.page = DragonMenu.Page.LIST;
        h.dragon = null;
        r.menu.render(r.p, h);
        r.p.messages.clear();
        r.click(30);
        expect(r.e.svc.isStaying(d) && r.e.svc.isStaying(teen) && r.p.heard("2 dragon(s) will wait where they are"), "all stay: " + r.p.messages);
        r.p.messages.clear();
        r.click(28);
        expect(!r.e.svc.isStaying(d) && !r.e.svc.isStaying(teen) && r.p.heard("2 dragon(s) follow you again"), "all follow: " + r.p.messages);
        r.p.messages.clear();
        r.click(28);
        expect(r.p.heard("None of your dragons was staying"), "all follow with nobody waiting");
        r.click(32);
        expect(r.e.svc.isStaying(d) && r.e.svc.isStaying(teen), "all stay at my spot");
        r.p.messages.clear();
        r.click(34);
        expect(h.page == DragonMenu.Page.LIST, "close leaves the page alone (the chest itself is closed)");
    }

    static void colours() {
        Rig r = new Rig();
        EnderDragon d = r.e.adult(r.p, 3, 64, 0);
        DragonColor now = r.e.svc.colorOf(d);
        DragonColor wanted = now == DragonColor.SKY ? DragonColor.ROSE : DragonColor.SKY;
        DragonMenu.Holder h = r.open();
        r.click(r.slotOf(h, r.e.svc.wordOf(d)));
        r.click(19);
        expect(h.page == DragonMenu.Page.COLOUR, "the colour page");
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < DragonMenu.SIZE; i++) {
            ItemStack it = h.getInventory().getItem(i);
            if (it != null && it.getType().name().endsWith("_DYE") && h.actions.containsKey(i)) {
                used.add(i);
            }
        }
        expect(used.size() == 16, "sixteen colours to pick from (" + used.size() + ")");
        int slot = -1;
        for (int s : used) {
            if (name(h.getInventory(), s).equals(wanted.display())) {
                slot = s;
            }
        }
        expect(slot >= 0, "the wanted colour is there");
        r.p.messages.clear();
        r.click(slot);
        expect(r.e.svc.colorOf(d) == now && r.p.heard("You need one"), "no dye, no change: " + r.p.messages);
        expect(h.page == DragonMenu.Page.COLOUR, "still choosing");
        r.p.getInventory().addItem(new ItemStack(Material.matchMaterial(wanted.dye()), 2));
        r.click(slot);
        expect(r.e.svc.colorOf(d) == wanted, "with a dye it changes");
        expect(r.p.heard("turns " + wanted.display().toLowerCase()), "and says so");
        int left = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = r.p.getInventory().getItem(i);
            if (st != null && st.getType() == Material.matchMaterial(wanted.dye())) {
                left += st.getAmount();
            }
        }
        expect(left == 1, "one dye was used (" + left + " left)");
        expect(h.page == DragonMenu.Page.DETAIL, "and it goes back to the dragon's page");
        // same colour again
        r.click(19);
        r.p.messages.clear();
        r.click(slot);
        expect(r.p.heard("already " + wanted.display().toLowerCase()) && h.page == DragonMenu.Page.COLOUR, "the same colour again: " + r.p.messages);
        int dyes = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = r.p.getInventory().getItem(i);
            if (st != null && st.getType() == Material.matchMaterial(wanted.dye())) {
                dyes += st.getAmount();
            }
        }
        expect(dyes == 1, "and no dye was taken");
        // creative players need no dye
        r.p.setGameMode(GameMode.CREATIVE);
        DragonColor other = wanted == DragonColor.ONYX ? DragonColor.IVORY : DragonColor.ONYX;
        int otherSlot = -1;
        for (int s : used) {
            if (name(h.getInventory(), s).equals(other.display())) {
                otherSlot = s;
            }
        }
        r.click(otherSlot);
        expect(r.e.svc.colorOf(d) == other, "creative needs no dye");
        // back from the colour page
        r.click(19);
        r.click(31);
        expect(h.page == DragonMenu.Page.DETAIL, "back from colours goes to the dragon");
    }

    static void words() {
        Rig r = new Rig();
        EnderDragon d = r.e.adult(r.p, 3, 64, 0);
        EnderDragon second = r.e.adult(r.p, 3, 64, 0);
        String old = r.e.svc.wordOf(d);
        DragonMenu.Holder h = r.open();
        r.click(r.slotOf(h, old));
        r.p.messages.clear();
        int closes = r.p.stubCloses;
        r.click(20);
        expect(r.p.stubCloses == closes + 1, "the menu closes so the player can type");
        expect(r.prompt.isAsking(r.p.getUniqueId()), "asks for the word in chat");
        expect(r.p.heard("Type the new word in chat") && r.p.heard("cancel"), "and explains: " + r.p.messages);
        // a good word
        int opens = r.p.stubOpens;
        AsyncPlayerChatEvent chat = new AsyncPlayerChatEvent(true, r.p, "  Drogon ", new HashSet<>());
        r.prompt.onChat(chat);
        expect(chat.isCancelled(), "the line is kept out of public chat");
        expect(!r.prompt.isAsking(r.p.getUniqueId()), "and the question is over");
        expect("drogon".equals(r.e.svc.wordOf(d)), "the word changed: " + r.e.svc.wordOf(d));
        expect(r.p.heard("answers to"), "and says so: " + r.p.messages);
        expect(r.p.stubOpens == opens + 1 && ((DragonMenu.Holder) r.p.stubOpened.getHolder()).page == DragonMenu.Page.DETAIL, "and the dragon's page opens again");
        expect(name(r.p.stubOpened, 4).equalsIgnoreCase("drogon"), "showing the new word");
        // ordinary chat from someone who was not asked is left alone
        AsyncPlayerChatEvent stray = new AsyncPlayerChatEvent(true, r.e.player("Chatty", 9, 64, 9), "hello", new HashSet<>());
        r.prompt.onChat(stray);
        expect(!stray.isCancelled(), "other people's chat is untouched");
        AsyncPlayerChatEvent after = new AsyncPlayerChatEvent(true, r.p, "hello all", new HashSet<>());
        r.prompt.onChat(after);
        expect(!after.isCancelled(), "and so is the owner's once the question is over");

        // a word that is not allowed
        DragonMenu.Holder h2 = r.open();
        r.click(r.slotOf(h2, "drogon"));
        r.click(20);
        r.p.messages.clear();
        r.prompt.onChat(new AsyncPlayerChatEvent(true, r.p, "stay", new HashSet<>()));
        expect("drogon".equals(r.e.svc.wordOf(d)) && r.p.heard("not one of"), "a reserved word is refused: " + r.p.messages);
        r.click(20);
        r.p.messages.clear();
        r.prompt.onChat(new AsyncPlayerChatEvent(true, r.p, r.e.svc.wordOf(second), new HashSet<>()));
        expect(r.p.heard("already has that word"), "a word another of yours has: " + r.p.messages);
        r.click(20);
        r.p.messages.clear();
        r.prompt.onChat(new AsyncPlayerChatEvent(true, r.p, "no way 123", new HashSet<>()));
        expect("drogon".equals(r.e.svc.wordOf(d)), "letters only");

        // cancel
        r.click(20);
        r.p.messages.clear();
        r.prompt.onChat(new AsyncPlayerChatEvent(true, r.p, "CANCEL", new HashSet<>()));
        expect(r.p.heard("Left the word as it was") && "drogon".equals(r.e.svc.wordOf(d)), "cancel gives up: " + r.p.messages);
        expect(!r.prompt.isAsking(r.p.getUniqueId()), "and ends the question");

        // the dragon goes away while the player is typing
        r.click(20);
        d.remove();
        r.e.svc.onDeath(d);
        r.p.messages.clear();
        r.prompt.onChat(new AsyncPlayerChatEvent(true, r.p, "newname", new HashSet<>()));
        expect(r.p.heard("out of reach"), "a dragon that left is reported: " + r.p.messages);
    }

    static void releasing() {
        Rig r = new Rig();
        EnderDragon d = r.e.adult(r.p, 3, 64, 0);
        EnderDragon keep = r.e.adult(r.p, 3, 64, 0);
        DragonMenu.Holder h = r.open();
        r.click(r.slotOf(h, r.e.svc.wordOf(d)));
        r.click(25);
        expect(h.page == DragonMenu.Page.CONFIRM, "asks to confirm");
        expect(name(h.getInventory(), 11).equals("Yes, let it go") && name(h.getInventory(), 15).equals("No, keep it"), "yes and no");
        expect(name(h.getInventory(), 13) != null, "shows which dragon");
        expect(r.e.manager.isTame(d), "nothing happened yet");
        r.click(15);
        expect(h.page == DragonMenu.Page.DETAIL && r.e.manager.isTame(d), "no keeps it");
        r.click(25);
        r.p.messages.clear();
        r.click(11);
        expect(!r.e.manager.isTame(d) && r.p.heard("wild again"), "yes lets it go: " + r.p.messages);
        expect(h.page == DragonMenu.Page.LIST && h.dragon == null, "and returns to the list");
        expect(r.slotOf(h, r.e.svc.wordOf(keep)) >= 0, "the other dragon is still listed");
        expect(r.e.svc.ownedCount(r.p.getUniqueId()) == 1, "and the count is one lower");
        // an empty list afterwards
        r.e.svc.release(r.p, keep);
        r.menu.render(r.p, h);
        expect("No dragons near enough".equals(name(h.getInventory(), 22)), "no dragons left");
    }

    // ------------------------------------------------------------------ safety

    static void safety() {
        Rig r = new Rig();
        EnderDragon d = r.e.adult(r.p, 3, 64, 0);
        DragonMenu.Holder h = r.open();
        // clicks in the player's own inventory, or on empty slots, are blocked but do nothing
        InventoryClickEvent mine = r.click(r.p, h.getInventory(), 5, InventoryType.SlotType.QUICKBAR);
        expect(mine.isCancelled(), "clicking your own inventory while the menu is open is blocked");
        InventoryClickEvent blank = r.click(0);
        expect(blank.isCancelled() && h.page == DragonMenu.Page.LIST, "an empty slot does nothing");
        InventoryClickEvent outside = r.click(r.p, h.getInventory(), -999, InventoryType.SlotType.OUTSIDE);
        expect(outside.isCancelled(), "clicking outside is blocked");
        // dragging items across the menu is blocked
        InventoryView view = new InventoryView();
        view.setTopInventory(h.getInventory());
        view.setBottomInventory(r.p.getInventory());
        view.setPlayer(r.p);
        InventoryDragEvent drag = new InventoryDragEvent(view);
        r.menu.onDrag(drag);
        expect(drag.isCancelled(), "dragging is blocked");
        // other inventories are none of the menu's business
        Inventory chest = org.bukkit.Bukkit.createInventory(null, 27, "Chest");
        InventoryView plain = new InventoryView();
        plain.setTopInventory(chest);
        plain.setBottomInventory(r.p.getInventory());
        plain.setPlayer(r.p);
        InventoryClickEvent chestClick = new InventoryClickEvent(plain, InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        r.menu.onClick(chestClick);
        expect(!chestClick.isCancelled(), "ordinary chests are left alone");
        InventoryDragEvent chestDrag = new InventoryDragEvent(plain);
        r.menu.onDrag(chestDrag);
        expect(!chestDrag.isCancelled(), "and ordinary dragging");
        InventoryView none = new InventoryView();
        InventoryClickEvent noTop = new InventoryClickEvent(none, InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        r.menu.onClick(noTop);
        expect(!noTop.isCancelled(), "a view with nothing in it is ignored");

        // somebody else clicking in this player's menu gets nothing
        Kit.TestPlayer snoop = r.e.player("Snoop", 5, 64, 5);
        r.click(r.slotOf(h, r.e.svc.wordOf(d)));
        InventoryClickEvent theirs = r.click(snoop, h.getInventory(), 11, InventoryType.SlotType.CONTAINER);
        expect(theirs.isCancelled() && !r.e.svc.isStaying(d), "another player cannot use it (and cannot take from it)");
        InventoryClickEvent theirBack = r.click(snoop, h.getInventory(), 31, InventoryType.SlotType.CONTAINER);
        expect(theirBack.isCancelled() && h.page == DragonMenu.Page.DETAIL, "nor can they turn its pages for the owner");

        // a dragon that moved out of reach while the page was open sends the player back to the list
        d.remove();
        r.e.svc.onDeath(d);
        r.click(11);
        expect(h.page == DragonMenu.Page.LIST, "a page for a dragon that is gone falls back to the list");
        // ...as does one that changed hands
        EnderDragon sold = r.e.adult(r.p, 3, 64, 0);
        h.page = DragonMenu.Page.DETAIL;
        h.dragon = sold.getUniqueId();
        r.menu.render(r.p, h);
        expect(h.page == DragonMenu.Page.DETAIL, "own dragon: stays on its page");
        r.e.manager.release(sold);
        r.menu.render(r.p, h);
        expect(h.page == DragonMenu.Page.LIST, "a dragon no longer yours: back to the list");
        // someone else's dragon can never be driven from this player's menu
        Kit.TestPlayer owner2 = r.e.player("Owner2", 70, 64, 0);
        EnderDragon theirsD = r.e.adult(owner2, 71, 64, 0);
        h.page = DragonMenu.Page.DETAIL;
        h.dragon = theirsD.getUniqueId();
        r.menu.render(r.p, h);
        expect(h.page == DragonMenu.Page.LIST, "another player's dragon cannot be opened");
        // titles and sizes
        expect(DragonMenu.SIZE == 36 && DragonMenu.SIZE % 9 == 0, "a whole number of rows");
        for (int slot : DragonMenu.DRAGON_SLOTS) {
            expect(slot % 9 != 0 && slot % 9 != 8, "dragon buttons keep off the edges");
        }
        expect(DragonMenu.capitalise("drogon").equals("Drogon") && DragonMenu.capitalise("").equals(""), "capitalise");
    }

    static void prompts() {
        Rig r = new Rig();
        List<String> heard = new ArrayList<>();
        List<String> gaveUp = new ArrayList<>();
        r.prompt.ask(r.p, heard::add, () -> gaveUp.add("up"));
        expect(r.prompt.isAsking(r.p.getUniqueId()), "asking");
        // a command abandons the question
        r.prompt.onCommand(new PlayerCommandPreprocessEvent(r.p, "/home"));
        expect(!r.prompt.isAsking(r.p.getUniqueId()) && gaveUp.size() == 1 && heard.isEmpty(), "running a command gives up");
        // leaving abandons it
        r.prompt.ask(r.p, heard::add, () -> gaveUp.add("up"));
        r.prompt.onQuit(new PlayerQuitEvent(r.p, ""));
        expect(!r.prompt.isAsking(r.p.getUniqueId()) && gaveUp.size() == 2, "so does logging out");
        // a new question replaces an old one, and tells the old one it lost
        r.prompt.ask(r.p, heard::add, () -> gaveUp.add("first"));
        r.prompt.ask(r.p, heard::add, () -> gaveUp.add("second"));
        expect(gaveUp.get(2).equals("first"), "asking again drops the earlier question");
        r.prompt.onChat(new AsyncPlayerChatEvent(true, r.p, "answer", new HashSet<>()));
        expect(heard.equals(List.of("answer")), "only the newer question gets the answer: " + heard);
        // waiting too long gives up (the timer the plugin set runs)
        r.prompt.ask(r.p, heard::add, () -> gaveUp.add("late"));
        Runnable timer = org.bukkit.scheduler.BukkitScheduler.LATER.get(org.bukkit.scheduler.BukkitScheduler.LATER.size() - 1);
        timer.run();
        expect(!r.prompt.isAsking(r.p.getUniqueId()) && gaveUp.get(gaveUp.size() - 1).equals("late"), "taking too long gives up");
        // an old timer does not cancel a newer question
        r.prompt.ask(r.p, heard::add, () -> gaveUp.add("a"));
        Runnable oldTimer = org.bukkit.scheduler.BukkitScheduler.LATER.get(org.bukkit.scheduler.BukkitScheduler.LATER.size() - 1);
        r.prompt.ask(r.p, heard::add, () -> gaveUp.add("b"));
        int before = gaveUp.size();
        oldTimer.run();
        expect(r.prompt.isAsking(r.p.getUniqueId()) && gaveUp.size() == before, "a stale timer leaves a newer question alone");
        // a command from someone not being asked changes nothing
        r.prompt.onCommand(new PlayerCommandPreprocessEvent(r.e.player("Bystander", 9, 64, 9), "/spawn"));
        expect(r.prompt.isAsking(r.p.getUniqueId()), "someone else's command does not matter");
        // a null message is treated as an empty answer, not a crash
        AsyncPlayerChatEvent blank = new AsyncPlayerChatEvent(true, r.p, null, new HashSet<>());
        r.prompt.onChat(blank);
        expect(blank.isCancelled() && heard.get(heard.size() - 1).isEmpty(), "an empty line is passed on as empty");
        new EntityDeathEvent(null);
    }
}
