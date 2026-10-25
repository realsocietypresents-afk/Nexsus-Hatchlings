package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A soft, reflection-based bridge to NexusHouses' API, used only to decide who a dragon must not attack: two players of the
 * same house, or of houses that stand together (liege, vassals, allies), count as allies. Without NexusHouses installed
 * every call says "not allied" and nothing else changes.
 */
public class HousesBridge {

    static final String API_CLASS_NAME = "com.nexuscraft.nexushouses.api.NexusHousesApi";

    private final Consumer<String> warn;
    private final String apiClassName;
    private Object api;
    private Method houseIdOf;
    private Method allianceBlockOf;
    private boolean notInstalled;
    private boolean warned;

    public HousesBridge(Consumer<String> warn) {
        this(warn, API_CLASS_NAME);
    }

    /** Package-private so a test can point the bridge at a pretend NexusHouses. */
    HousesBridge(Consumer<String> warn, String apiClassName) {
        this.warn = warn;
        this.apiClassName = apiClassName;
    }

    /** True if the two players share a house or belong to houses on the same side. */
    public boolean allied(UUID a, UUID b) {
        if (a == null || b == null || !resolved()) {
            return false;
        }
        try {
            Object houseA = houseIdOf.invoke(api, a);
            Object houseB = houseIdOf.invoke(api, b);
            if (houseA == null || houseB == null) {
                return false;
            }
            if (houseA.equals(houseB)) {
                return true;
            }
            Object block = allianceBlockOf.invoke(api, houseA);
            return block instanceof Set<?> set && set.contains(houseB);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return false;
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
            Class<?> found = Class.forName(apiClassName);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(found);
            if (registration == null) {
                return false;
            }
            houseIdOf = found.getMethod("houseIdOf", UUID.class);
            allianceBlockOf = found.getMethod("allianceBlockOf", UUID.class);
            api = registration.getProvider();
            return true;
        } catch (ClassNotFoundException e) {
            notInstalled = true;
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
        warn.accept("[NexusHatchlings] NexusHouses is installed, but did not answer the way this version expects, so dragons will not "
                + "spare house allies. (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    }
}
