package com.nexuscraft.nexushatchlings;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Asks a player to type something in chat and hands their next chat line to a callback instead of sending it to everyone.
 * Chat is used rather than an anvil box because it works the same for Java and Bedrock players. "cancel" gives up, a command
 * or leaving the game also gives up, and so does waiting too long.
 */
public final class ChatPrompt implements Listener {

    private static final long TIMEOUT_TICKS = 20L * 45L;

    private record Pending(long token, Consumer<String> answer, Runnable giveUp) {
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private long nextToken;

    public ChatPrompt(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAsking(UUID player) {
        return pending.containsKey(player);
    }

    /**
     * Waits for this player's next chat line. {@code giveUp} runs when they type cancel, run a command, leave, or take too long.
     * A question already waiting for this player is dropped (its {@code giveUp} runs) in favour of the new one.
     */
    public void ask(Player player, Consumer<String> answer, Runnable giveUp) {
        Pending old = pending.remove(player.getUniqueId());
        if (old != null) {
            old.giveUp().run();
        }
        long token = ++nextToken;
        pending.put(player.getUniqueId(), new Pending(token, answer, giveUp));
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending now = pending.get(player.getUniqueId());
            if (now != null && now.token() == token) {
                pending.remove(player.getUniqueId());
                now.giveUp().run();
            }
        }, TIMEOUT_TICKS);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.get(player.getUniqueId());
        if (p == null) {
            return;
        }
        event.setCancelled(true);
        String text = event.getMessage() == null ? "" : event.getMessage().trim();
        // Chat arrives off the main thread; the answer is acted on back on it.
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            Pending still = pending.get(player.getUniqueId());
            if (still == null || still.token() != p.token()) {
                return;
            }
            pending.remove(player.getUniqueId());
            if (text.toLowerCase(Locale.ROOT).equals("cancel")) {
                still.giveUp().run();
            } else {
                still.answer().accept(text);
            }
        });
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        drop(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drop(event.getPlayer().getUniqueId());
    }

    private void drop(UUID id) {
        Pending p = pending.remove(id);
        if (p != null) {
            p.giveUp().run();
        }
    }
}
