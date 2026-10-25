package com.nexuscraft.nexushatchlings;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Sends the {@code /nexushatchlings} overview automatically the first time a player ever joins with
 * this plugin installed (gated on {@code settings.first-join-message} in config.yml -- on by
 * default, so nobody has to already know the command exists to find out how taming, growth, and
 * traits work). "First time" is tracked in a tiny flat file, {@code seen.log}, one UUID per line --
 * identical approach to NexusWards' WardFirstJoinListener and NexusForge's ForgeFirstJoinListener,
 * deliberately kept consistent rather than inventing a third pattern for the same problem.
 */
public final class HatchlingFirstJoinListener implements Listener {

    private final File file;
    private final Logger logger;
    private final HatchlingConfig config;
    private final TraitRegistry traitRegistry;
    private final boolean enabled;
    private final Set<UUID> seen = new HashSet<>();
    private BufferedWriter writer;

    public HatchlingFirstJoinListener(File dataFolder, Logger logger, HatchlingConfig config,
                                       TraitRegistry traitRegistry, boolean enabled) {
        this.file = new File(dataFolder, "seen.log");
        this.logger = logger;
        this.config = config;
        this.traitRegistry = traitRegistry;
        this.enabled = enabled;
    }

    public void load() {
        if (!enabled) {
            return;
        }
        seen.clear();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        seen.add(UUID.fromString(line.trim()));
                    } catch (IllegalArgumentException ignored) {
                        // Malformed line -- skip it, never fatal.
                    }
                }
            } catch (IOException ex) {
                logger.warning("[NexusHatchlings] Could not read seen.log -- every player will be treated as new "
                        + "this boot: " + ex.getMessage());
            }
        }
        try {
            file.getParentFile().mkdirs();
            writer = new BufferedWriter(new FileWriter(file, true));
        } catch (IOException ex) {
            logger.warning("[NexusHatchlings] Could not open seen.log for writing -- the first-join message will "
                    + "repeat every boot this session: " + ex.getMessage());
            writer = null;
        }
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Shutting down anyway.
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (seen.contains(uuid)) {
            return;
        }
        seen.add(uuid);
        markSeen(uuid);
        HatchlingMessages.sendOverview(player, config, traitRegistry);
    }

    private void markSeen(UUID uuid) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(uuid.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            logger.warning("[NexusHatchlings] Failed writing to seen.log: " + ex.getMessage());
        }
    }
}
