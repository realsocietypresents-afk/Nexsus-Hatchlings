package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /dragon menu}: a chest menu where every dragon is a button of its own (so they are told apart even when they stand on
 * top of each other in the world), and clicking one opens a page of what can be done with it. One chest is used for every page and
 * redrawn in place, so moving between pages does not flicker, and chat is used for anything that has to be typed, so it works on
 * Bedrock too. Nothing can be taken out of the menu.
 */
public final class DragonMenu implements Listener {

    /** The pages of the menu. */
    enum Page { LIST, DETAIL, COLOUR, CONFIRM }

    static final int SIZE = 36;
    static final String TITLE = "Your Dragons";
    /** Where the dragon buttons go: two rows of seven, so up to fourteen (the limit is ten by default). */
    static final int[] DRAGON_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    interface Click {
        void run(Player player);
    }

    /** What a chest remembers about who is looking at it and what it shows. */
    static final class Holder implements InventoryHolder {
        final UUID viewer;
        Page page = Page.LIST;
        UUID dragon;
        Inventory inventory;
        final Map<Integer, Click> actions = new HashMap<>();

        Holder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final DragonService dragons;
    private final ChatPrompt prompt;

    public DragonMenu(DragonService dragons, ChatPrompt prompt) {
        this.dragons = dragons;
        this.prompt = prompt;
    }

    // ------------------------------------------------------------------ opening

    /** Opens the list of this player's dragons. */
    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    /** Opens the page for one dragon straight away. */
    public void openDetail(Player player, EnderDragon dragon) {
        Holder holder = new Holder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.page = Page.DETAIL;
        holder.dragon = dragon.getUniqueId();
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    // ------------------------------------------------------------------ drawing

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material dyeOf(DragonColor color) {
        Material m = Material.matchMaterial(color.dye());
        return m == null ? Material.PAPER : m;
    }

    private void set(Holder h, int slot, ItemStack item, Click click) {
        h.inventory.setItem(slot, item);
        if (click != null) {
            h.actions.put(slot, click);
        }
    }

    /** Redraws whatever page the chest is on. */
    void render(Player player, Holder h) {
        for (int i = 0; i < SIZE; i++) {
            h.inventory.setItem(i, null);
        }
        h.actions.clear();
        EnderDragon d = h.dragon == null ? null : dragons.byId(h.dragon);
        if (h.page != Page.LIST && (d == null || !player.getUniqueId().equals(dragons.ownerOf(d)))) {
            // The dragon went out of reach (unloaded, died, was released): fall back to the list.
            h.page = Page.LIST;
            h.dragon = null;
            d = null;
        }
        switch (h.page) {
            case LIST -> drawList(player, h);
            case DETAIL -> drawDetail(player, h, d);
            case COLOUR -> drawColours(player, h, d);
            case CONFIRM -> drawConfirm(player, h, d);
        }
    }

    private List<EnderDragon> mine(Player player) {
        List<EnderDragon> list = dragons.dragonsOf(player.getUniqueId());
        list.sort(Comparator.comparing((EnderDragon e) -> String.valueOf(dragons.wordOf(e))).thenComparing(e -> e.getUniqueId().toString()));
        return list;
    }

    private void drawList(Player player, Holder h) {
        List<EnderDragon> list = mine(player);
        int owned = dragons.ownedCount(player.getUniqueId());
        List<String> head = new ArrayList<>();
        head.add("§7Dragons: §f" + owned + "§7 of §f" + dragons.limit());
        if (owned > list.size()) {
            head.add("§7" + (owned - list.size()) + " more asleep too far away to show here.");
        }
        head.add("§7Click a dragon to give it orders.");
        set(h, 4, icon(Material.DRAGON_EGG, "§dYour dragons", head), null);
        if (list.isEmpty()) {
            set(h, 22, icon(Material.BARRIER, "§cNo dragons near enough",
                    List.of("§7Put a Dragon Egg on a lit campfire, then", "§7throw a Hatchling Crystal at the baby.")), null);
        }
        for (int i = 0; i < list.size() && i < DRAGON_SLOTS.length; i++) {
            EnderDragon d = list.get(i);
            UUID id = d.getUniqueId();
            set(h, DRAGON_SLOTS[i], dragonIcon(d), p -> {
                h.page = Page.DETAIL;
                h.dragon = id;
                render(p, h);
            });
        }
        set(h, 28, icon(Material.LEAD, "§aAll follow me", List.of("§7Every dragon comes back to you.")), p -> {
            int n = dragons.followAll(p.getUniqueId());
            p.sendMessage(DragonText.PREFIX + (n == 0 ? "None of your dragons was staying." : n + " dragon(s) follow you again."));
            render(p, h);
        });
        set(h, 30, icon(Material.RED_BED, "§eAll stay where they are", List.of("§7Each dragon waits right where it is now.")), p -> {
            int n = dragons.stayAll(p, false);
            p.sendMessage(DragonText.PREFIX + n + " dragon(s) will wait where they are.");
            render(p, h);
        });
        set(h, 32, icon(Material.CAMPFIRE, "§eAll stay at my spot", List.of("§7They gather around where you stand and wait there.")), p -> {
            int n = dragons.stayAll(p, true);
            p.sendMessage(DragonText.PREFIX + n + " dragon(s) will wait around you here.");
            render(p, h);
        });
        set(h, 34, icon(Material.BARRIER, "§cClose", List.of()), Player::closeInventory);
    }

    private ItemStack dragonIcon(EnderDragon d) {
        DragonColor color = dragons.colorOf(d);
        String word = dragons.wordOf(d);
        List<String> lore = new ArrayList<>();
        lore.add("§7Stage: §f" + dragons.stageOf(d).display() + " §7(" + Math.round(dragons.progressOf(d) * 100.0) + "% grown)");
        lore.add("§7Doing: §f" + dragons.statusOf(d));
        lore.add("§7Word: §f" + (word == null ? "(none)" : word));
        lore.add("§7Colour: §f" + color.display());
        if (dragons.isStaying(d)) {
            DragonService.Spot s = dragons.stayOf(d);
            lore.add("§7Waiting at: §f" + Math.round(s.x()) + ", " + Math.round(s.y()) + ", " + Math.round(s.z()));
        }
        lore.add("§eClick for orders");
        return icon(dyeOf(color), "§" + color.chatCode() + (word == null ? color.display() + " Dragon" : capitalise(word)), lore);
    }

    static String capitalise(String word) {
        return word.isEmpty() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }

    private void drawDetail(Player player, Holder h, EnderDragon d) {
        UUID id = d.getUniqueId();
        set(h, 4, dragonIcon(d), null);
        set(h, 10, icon(Material.LEAD, "§aFollow me", List.of("§7It comes back to you and trails behind.")), p -> {
            EnderDragon now = dragons.byId(id);
            if (now != null) {
                p.sendMessage(DragonText.PREFIX + (dragons.follow(now) ? "Your dragon follows you again." : "It was already following you."));
            }
            render(p, h);
        });
        set(h, 11, icon(Material.RED_BED, "§eStay where it is", List.of("§7It waits at this very spot.")), p -> stayClick(p, h, id, false));
        set(h, 12, icon(Material.CAMPFIRE, "§eStay at my spot", List.of("§7It comes to where you stand and waits there.")), p -> stayClick(p, h, id, true));
        boolean adult = dragons.stageOf(d).canAttack();
        set(h, 14, icon(adult ? Material.IRON_SWORD : Material.BARRIER, adult ? "§cAttack the nearest player" : "§7Attack (not fully grown)",
                adult ? List.of("§7It goes after whoever is closest to it.", "§7Never you or anyone allied with you.") : List.of("§7Only a fully grown dragon can fight.")), p -> {
            EnderDragon now = dragons.byId(id);
            if (now == null) {
                render(p, h);
                return;
            }
            DragonService.OrderResult result = dragons.attack(p, now, Targeting.Mode.NEAREST, null);
            if (result.status() == DragonService.Status.STARTED) {
                p.sendMessage(DragonText.PREFIX + "Your dragon roars and goes after " + DragonText.joinNames(result.names()) + ".");
            } else {
                p.sendMessage(DragonText.orderFailure(result, String.valueOf(dragons.wordOf(now)), null));
            }
            render(p, h);
        });
        set(h, 15, icon(Material.SHIELD, "§aCall off the attack", List.of("§7It stops fighting and comes back.")), p -> {
            EnderDragon now = dragons.byId(id);
            if (now != null) {
                p.sendMessage(DragonText.PREFIX + (dragons.stop(now) ? "Your dragon backs off." : "It was not fighting."));
            }
            render(p, h);
        });
        set(h, 19, icon(Material.PAINTING, "§dChange colour", List.of("§7Costs one matching dye.")), p -> {
            h.page = Page.COLOUR;
            render(p, h);
        });
        set(h, 20, icon(Material.NAME_TAG, "§bChange command word", List.of("§7The word you type after /dragon to command it.", "§7You type the new one in chat.")), p -> askWord(p, h, id));
        set(h, 25, icon(Material.TNT, "§4Let it go", List.of("§7It becomes wild again and is no longer yours.", "§7You will be asked to confirm.")), p -> {
            h.page = Page.CONFIRM;
            render(p, h);
        });
        set(h, 31, icon(Material.ARROW, "§7Back to all dragons", List.of()), p -> {
            h.page = Page.LIST;
            h.dragon = null;
            render(p, h);
        });
    }

    private void stayClick(Player p, Holder h, UUID id, boolean atPlayer) {
        EnderDragon now = dragons.byId(id);
        if (now != null) {
            DragonService.StayResult result = dragons.stay(p, now, atPlayer);
            p.sendMessage(DragonText.stayMessage(result, atPlayer));
        }
        render(p, h);
    }

    private void drawColours(Player player, Holder h, EnderDragon d) {
        UUID id = d.getUniqueId();
        DragonColor[] all = DragonColor.values();
        set(h, 4, icon(Material.PAINTING, "§dPick a colour", List.of("§7Each costs one matching dye.", "§7It is now " + dragons.colorOf(d).display().toLowerCase() + ".")), null);
        for (int i = 0; i < all.length; i++) {
            DragonColor color = all[i];
            int slot = i < 8 ? 10 + i : 19 + (i - 8);
            set(h, slot, icon(dyeOf(color), "§" + color.chatCode() + color.display(), List.of("§7Needs: §f" + color.dye().toLowerCase().replace('_', ' '))), p -> recolour(p, h, id, color));
        }
        set(h, 31, icon(Material.ARROW, "§7Back", List.of()), p -> {
            h.page = Page.DETAIL;
            render(p, h);
        });
    }

    private void recolour(Player p, Holder h, UUID id, DragonColor color) {
        EnderDragon now = dragons.byId(id);
        if (now == null) {
            render(p, h);
            return;
        }
        if (dragons.colorOf(now) == color) {
            p.sendMessage(DragonText.PREFIX + "It is already " + color.display().toLowerCase() + ".");
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            Material dye = dyeOf(color);
            if (!p.getInventory().removeItem(new ItemStack(dye, 1)).isEmpty()) {
                p.sendMessage(DragonText.PREFIX + "You need one " + color.dye().toLowerCase().replace('_', ' ') + " for that.");
                return;
            }
        }
        dragons.setColor(now, color);
        p.sendMessage(DragonText.PREFIX + "The dragon turns " + color.display().toLowerCase() + ".");
        h.page = Page.DETAIL;
        render(p, h);
    }

    private void drawConfirm(Player player, Holder h, EnderDragon d) {
        UUID id = d.getUniqueId();
        set(h, 13, dragonIcon(d), null);
        set(h, 11, icon(Material.LIME_DYE, "§aYes, let it go", List.of("§7It will be a wild dragon again.", "§7Anyone could tame it.")), p -> {
            EnderDragon now = dragons.byId(id);
            if (now != null && dragons.release(p, now)) {
                p.sendMessage(DragonText.PREFIX + "Your dragon is wild again.");
            }
            h.page = Page.LIST;
            h.dragon = null;
            render(p, h);
        });
        set(h, 15, icon(Material.RED_DYE, "§cNo, keep it", List.of()), p -> {
            h.page = Page.DETAIL;
            render(p, h);
        });
    }

    // ------------------------------------------------------------------ typing a new word

    private void askWord(Player p, Holder h, UUID id) {
        p.closeInventory();
        p.sendMessage(DragonText.PREFIX + "Type the new word in chat (" + CommandWords.MIN_LENGTH + " to " + CommandWords.MAX_LENGTH
                + " letters, nothing else), or type cancel.");
        prompt.ask(p, text -> {
            EnderDragon now = dragons.byId(id);
            if (now == null) {
                p.sendMessage(DragonText.PREFIX + "That dragon is out of reach now.");
                return;
            }
            DragonService.WordResult result = dragons.setWord(now, p.getUniqueId(), text);
            p.sendMessage(DragonText.wordMessage(result, text));
            if (result == DragonService.WordResult.OK) {
                Holder again = new Holder(p.getUniqueId());
                again.inventory = Bukkit.createInventory(again, SIZE, TITLE);
                again.page = Page.DETAIL;
                again.dragon = id;
                render(p, again);
                p.openInventory(again.inventory);
            }
        }, () -> p.sendMessage(DragonText.PREFIX + "Left the word as it was."));
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.viewer)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        Click click = holder.actions.get(event.getSlot());
        if (click != null) {
            click.run(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
