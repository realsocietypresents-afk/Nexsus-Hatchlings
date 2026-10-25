package com.nexuscraft.nexushatchlings;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NexusHatchlingsPlugin extends JavaPlugin {

    private HatchlingConfig config;
    private HatchlingKeys keys;
    private TraitRegistry traitRegistry;
    private TraitManager traitManager;
    private HatchlingManager manager;
    private CrystalItem crystalItem;
    private CrystalTaming taming;
    private HatchlingFirstJoinListener firstJoinListener;
    private BukkitTask reapplyTask;
    private BukkitTask scanTask;
    private DragonConfig dragonConfig;
    private DragonService dragons;
    private DragonEggs dragonEggs;
    private DragonRoster dragonRoster;
    private final java.util.List<BukkitTask> dragonTasks = new java.util.ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.keys = new HatchlingKeys(this);
        this.config = new HatchlingConfig(this);
        config.load();

        this.traitRegistry = new TraitRegistry(this);
        traitRegistry.load();
        this.traitManager = new TraitManager(traitRegistry, keys, config);

        this.manager = new HatchlingManager(this, config, keys, traitManager);
        this.crystalItem = new CrystalItem(keys);

        getServer().getPluginManager().registerEvents(new CrystalThrowListener(keys, crystalItem), this);
        this.taming = new CrystalTaming(config, manager, crystalItem);
        getServer().getPluginManager().registerEvents(new CrystalImpactListener(keys, manager, taming), this);
        getServer().getPluginManager().registerEvents(new HatchlingListener(config, manager, keys), this);
        getServer().getPluginManager().registerEvents(new TraitAbilityListener(traitManager), this);
        getServer().getPluginManager().registerEvents(new OwnerInteractListener(config, manager, getLogger()::info), this);

        boolean firstJoinMessage = getConfig().getBoolean("settings.first-join-message", true);
        this.firstJoinListener = new HatchlingFirstJoinListener(getDataFolder(), getLogger(), config,
                traitRegistry, firstJoinMessage);
        firstJoinListener.load();
        getServer().getPluginManager().registerEvents(firstJoinListener, this);

        NexusHatchlingsCommand hatchlingsCommand =
                new NexusHatchlingsCommand(this, config, manager, crystalItem, traitRegistry, traitManager);
        hatchlingsCommand.setReloadHook(this::reloadDragons);
        getCommand("nexushatchlings").setExecutor(hatchlingsCommand);

        setUpDragons();

        if (config.recipeEnabled()) {
            registerRecipe();
        }

        // Anything already tamed and loaded (a /reload, or this plugin being installed onto a
        // running server) is picked up immediately rather than waiting for the next scheduled tick.
        manager.reapplyAllLoaded();

        startReapplyTask();
        startScanTask();

        getLogger().info("NexusHatchlings enabled -- crystal tames any mob at scale " + config.startScale()
                + "x, growing to " + config.maxScale() + "x over " + config.daysToGrow() + " real day(s), "
                + traitRegistry.all().size() + " possible traits.");
    }

    private void setUpDragons() {
        this.dragonConfig = new DragonConfig();
        dragonConfig.load(getConfig(), getLogger()::warning);
        HousesBridge houses = new HousesBridge(getLogger()::warning);
        this.dragons = new DragonService(this, config, dragonConfig, keys, manager, houses, new java.util.Random());
        manager.setDragons(dragons);
        taming.setDragons(dragons);
        this.dragonRoster = new DragonRoster(getDataFolder(), getLogger()::warning, System::currentTimeMillis);
        dragonRoster.load();
        dragons.setRoster(dragonRoster);
        dragons.setModels(new DragonModels(this, dragonConfig));
        this.dragonEggs = new DragonEggs(getDataFolder(), getLogger()::warning, dragonConfig, dragons, System::currentTimeMillis);
        dragonEggs.load();

        getServer().getPluginManager().registerEvents(new DragonListener(dragonConfig, dragons, dragonEggs), this);
        ChatPrompt chatPrompt = new ChatPrompt(this);
        DragonMenu menu = new DragonMenu(dragons, chatPrompt);
        getServer().getPluginManager().registerEvents(chatPrompt, this);
        getServer().getPluginManager().registerEvents(menu, this);
        DragonCommand command = new DragonCommand(dragons, dragonEggs);
        command.setMenu(menu);
        org.bukkit.command.PluginCommand dragonCommand = getCommand("dragon");
        if (dragonCommand != null) {
            dragonCommand.setExecutor(command);
            dragonCommand.setTabCompleter(command);
        }
        scheduleDragons();
        getLogger().info("Dragons " + (dragonConfig.enabled ? "enabled" : "switched off") + ": " + dragonEggs.size()
                + " egg(s) warming, hatch time " + dragonConfig.hatchMinutes + " minute(s).");
    }

    private void scheduleDragons() {
        cancelDragonTasks();
        if (!dragonConfig.enabled) {
            return;
        }
        dragonTasks.add(getServer().getScheduler().runTaskTimer(this, dragons::tick, 20L, dragonConfig.moveIntervalTicks));
        dragonTasks.add(getServer().getScheduler().runTaskTimer(this, dragons::aura, 40L, dragonConfig.auraIntervalTicks));
        dragonTasks.add(getServer().getScheduler().runTaskTimer(this, dragons::scan, 20L, 100L));
        long eggTicks = dragonConfig.hatchCheckSeconds * 20L;
        dragonTasks.add(getServer().getScheduler().runTaskTimer(this, dragonEggs::tick, eggTicks, eggTicks));
    }

    private void cancelDragonTasks() {
        for (BukkitTask task : dragonTasks) {
            task.cancel();
        }
        dragonTasks.clear();
    }

    private void reloadDragons() {
        dragonConfig.load(getConfig(), getLogger()::warning);
        scheduleDragons();
        dragons.refresh();
    }

    private void registerRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "hatchling-crystal");
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, crystalItem.create());
        recipe.addIngredient(Material.AMETHYST_SHARD);
        recipe.addIngredient(Material.GOLD_INGOT);
        recipe.addIngredient(Material.SLIME_BALL);
        recipe.addIngredient(Material.ENDER_PEARL);
        Bukkit.addRecipe(recipe);
    }

    private void startReapplyTask() {
        if (reapplyTask != null) {
            reapplyTask.cancel();
        }
        long intervalTicks = 20L * 60 * Math.max(1, config.reapplyIntervalMinutes());
        this.reapplyTask = getServer().getScheduler().runTaskTimer(this,
                manager::reapplyAllLoaded, intervalTicks, intervalTicks);
    }

    private void startScanTask() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        long intervalTicks = 20L * Math.max(1, config.crystalScanIntervalSeconds());
        CrystalScanTask task = new CrystalScanTask(this, config, keys, manager, taming);
        this.scanTask = getServer().getScheduler().runTaskTimer(this, task, intervalTicks, intervalTicks);
    }

    @Override
    public void onDisable() {
        if (reapplyTask != null) {
            reapplyTask.cancel();
        }
        if (scanTask != null) {
            scanTask.cancel();
        }
        cancelDragonTasks();
        if (dragonEggs != null) {
            dragonEggs.flush();
        }
        if (dragons != null) {
            dragons.shutdown();
        }
        if (firstJoinListener != null) {
            firstJoinListener.close();
        }
        getLogger().info("NexusHatchlings disabled.");
    }
}
