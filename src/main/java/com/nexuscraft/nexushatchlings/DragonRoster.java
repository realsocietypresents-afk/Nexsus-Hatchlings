package com.nexuscraft.nexushatchlings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Who owns which dragon, kept in {@code roster.tsv}, so the limit on dragons per player counts the ones that are asleep in
 * unloaded chunks too (the owner is otherwise written only on the dragon itself). An entry is added when a dragon is tamed
 * or seen, removed when it is released or dies, and dropped when its dragon has not been seen for a long time, so a dragon
 * that was removed by some other means cannot use up a place for ever.
 */
public final class DragonRoster {

    private static final class Entry {
        UUID owner;
        long seen;
    }

    /** A seen-time is only written to disk when it has moved on by this much, so a tick of "still here" does not rewrite the file. */
    static final long SEEN_STEP_MILLIS = 600_000L;

    private final File file;
    private final Consumer<String> warn;
    private final LongSupplier clock;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private boolean dirty;

    public DragonRoster(File dataFolder, Consumer<String> warn, LongSupplier clock) {
        this.file = new File(dataFolder, "roster.tsv");
        this.warn = warn;
        this.clock = clock;
    }

    public int size() {
        return entries.size();
    }

    /** How many dragons this player has, loaded or not. */
    public int count(UUID owner) {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.owner.equals(owner)) {
                n++;
            }
        }
        return n;
    }

    public boolean has(UUID dragon) {
        return entries.containsKey(dragon);
    }

    public UUID ownerOf(UUID dragon) {
        Entry e = entries.get(dragon);
        return e == null ? null : e.owner;
    }

    /** Records that this player owns this dragon (and that it was seen just now). Moves it if it had another owner. */
    public void add(UUID owner, UUID dragon) {
        Entry e = entries.get(dragon);
        if (e == null) {
            e = new Entry();
            entries.put(dragon, e);
            dirty = true;
        }
        if (!owner.equals(e.owner)) {
            e.owner = owner;
            dirty = true;
        }
        touch(e);
    }

    /** Notes that a dragon is alive right now. Does nothing for one that is not on the roster. */
    public void seen(UUID dragon) {
        Entry e = entries.get(dragon);
        if (e != null) {
            touch(e);
        }
    }

    private void touch(Entry e) {
        long now = clock.getAsLong();
        if (now - e.seen >= SEEN_STEP_MILLIS) {
            e.seen = now;
            dirty = true;
        }
    }

    public void remove(UUID dragon) {
        if (entries.remove(dragon) != null) {
            dirty = true;
        }
    }

    /** Drops every dragon that has not been seen for {@code staleMillis}. Returns how many went. */
    public int prune(long staleMillis) {
        long now = clock.getAsLong();
        List<UUID> stale = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            if (now - e.getValue().seen > staleMillis) {
                stale.add(e.getKey());
            }
        }
        for (UUID id : stale) {
            entries.remove(id);
        }
        if (!stale.isEmpty()) {
            dirty = true;
        }
        return stale.size();
    }

    // ------------------------------------------------------------------ the file

    public void load() {
        entries.clear();
        if (!file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length < 3) {
                    warn.accept("roster.tsv: skipped a line that is not three columns: " + line);
                    continue;
                }
                try {
                    Entry e = new Entry();
                    e.owner = UUID.fromString(parts[1].trim());
                    e.seen = Long.parseLong(parts[2].trim());
                    entries.put(UUID.fromString(parts[0].trim()), e);
                } catch (IllegalArgumentException bad) {
                    warn.accept("roster.tsv: skipped a line that could not be read: " + line);
                }
            }
        } catch (IOException e) {
            warn.accept("roster.tsv could not be read: " + e.getMessage());
        }
        dirty = false;
    }

    /** Writes the file if anything changed. */
    public void flush() {
        if (!dirty) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            warn.accept("roster.tsv: could not create " + parent);
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("# dragon id <tab> owner id <tab> last seen (ms)");
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            lines.add(e.getKey() + "\t" + e.getValue().owner + "\t" + e.getValue().seen);
        }
        try {
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            warn.accept("roster.tsv could not be written: " + e.getMessage());
        }
    }
}
