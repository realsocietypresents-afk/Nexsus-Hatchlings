package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A soft, reflection-based, OUTBOUND bridge to NexusWaiting's WaitingApi: when a hatchling is tamed
 * this hands NexusWaiting one timer ("your hatchling finishes growing at ..."), so the owner sees it
 * on their /waiting list at login and gets a Discord ping if it finishes while they are offline.
 *
 * Deliberately not a compile-time dependency (same Class.forName + ServicesManager pattern as the
 * rest of this plugin family): NexusHatchlings works exactly as before if NexusWaiting is not
 * installed at all, every call below then being a silent no-op.
 */
final class WaitingBridge {

    static final String API_CLASS_NAME = "com.nexuscraft.nexuswaiting.WaitingApi";

    private final Consumer<String> warn;
    private Object api;
    private Method setMethod;
    private Method clearMethod;
    private boolean notInstalled;
    private boolean warned;

    WaitingBridge(Consumer<String> warn) {
        this.warn = warn;
    }

    /** Puts (or refreshes) one timer. Same key and same ready time again changes nothing on their side. */
    void set(UUID player, String key, String label, long readyAtMillis) {
        if (!resolved()) {
            return;
        }
        try {
            setMethod.invoke(api, player, key, label, readyAtMillis);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
        }
    }

    /** Removes a timer (the hatchling was released or died). */
    void clear(UUID player, String key) {
        if (!resolved()) {
            return;
        }
        try {
            clearMethod.invoke(api, player, key);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
        }
    }

    private boolean resolved() {
        if (api != null) {
            return true;
        }
        if (notInstalled) {
            return false;
        }
        try {
            Class<?> found = Class.forName(API_CLASS_NAME);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(found);
            if (registration == null) {
                return false; // installed but not (yet) running: try again next time
            }
            Object provider = registration.getProvider();
            setMethod = found.getMethod("set", UUID.class, String.class, String.class, long.class);
            clearMethod = found.getMethod("clear", UUID.class, String.class);
            api = provider;
            return true;
        } catch (ClassNotFoundException e) {
            notInstalled = true; // NexusWaiting is not installed -- perfectly fine, nothing to feed
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            notInstalled = true;
            warnOnce(e);
            return false;
        }
    }

    private void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        warn.accept("[NexusHatchlings] NexusWaiting is installed, but did not accept a timer the way this version expects -- "
                + "growing hatchlings just will not show on the Waiting list. (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    }
}
