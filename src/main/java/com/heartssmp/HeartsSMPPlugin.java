package com.heartssmp;

import com.heartssmp.commands.*;
import com.heartssmp.data.DataManager;
import com.heartssmp.god.*;
import com.heartssmp.listeners.*;
import com.heartssmp.managers.GemManager;
import com.heartssmp.managers.ItemManager;
import com.heartssmp.managers.SkillManager;
import com.heartssmp.quest.DivineTrialManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class HeartsSMPPlugin extends JavaPlugin {

    private static HeartsSMPPlugin instance;

    private DataManager        dataManager;
    private GodManager         godManager;
    private DivineWorldManager divineWorldManager;
    private DivineTrialManager divineTrialManager;
    private GemManager         gemManager;
    private SkillManager       skillManager;
    private ItemManager        itemManager;
    private SkillAbilityListener skillAbilityListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        dataManager        = new DataManager(this);
        itemManager        = new ItemManager(this);
        gemManager         = new GemManager(this);
        skillManager       = new SkillManager(this);
        divineWorldManager = new DivineWorldManager(this);
        divineTrialManager = new DivineTrialManager(this);
        godManager         = new GodManager(this);

        skillAbilityListener = new SkillAbilityListener(this);
        getServer().getPluginManager().registerEvents(new ItemProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GemAbilityListener(this),    this);

        registerCommands();

        new BukkitRunnable() {
            @Override public void run() {
                for (var player : getServer().getOnlinePlayers()) {
                    if (player.isDead()) continue;
                    gemManager.runPassiveTick(player);
                    skillManager.runPassiveTick(player);
                    skillAbilityListener.dispatchPassiveTick(player);
                }
            }
        }.runTaskTimer(this, 20L, 4L);

        new BukkitRunnable() {
            @Override public void run() { dataManager.saveAll(); }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L);

        getLogger().info("╔════════════════════════════════════╗");
        getLogger().info("║  HeartsSMP v2.0 — FULLY LOADED     ║");
        getLogger().info("║  AI: " + padRight(getConfig().getString("ai.provider","anthropic").toUpperCase()
                + " / " + getConfig().getString("ai.model","?"), 27) + "║");
        getLogger().info("║  12 Castle Trials | Divine Dimension║");
        getLogger().info("╚════════════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        godManager.despawnAll();
        dataManager.saveAll();
        getLogger().info("[HeartsSMP] Disabled — all data saved.");
    }

    private void registerCommands() {
        var godAdminCmd = getCommand("godadmin");
        if (godAdminCmd != null) {
            GodAdminCommand gadmin = new GodAdminCommand(this);
            godAdminCmd.setExecutor(gadmin);
            godAdminCmd.setTabCompleter(gadmin);
        }
        registerIfPresent("adminhearts",    new AdminHeartsCommand(this));
        registerIfPresent("adminlives",     new AdminLivesCommand(this));
        registerIfPresent("admingem",       new AdminGemCommand(this));
        registerIfPresent("adminskill",     new AdminSkillCommand(this));
        registerIfPresent("adminitem",      new AdminItemCommand(this));
        registerIfPresent("adminunban",     new AdminUnbanCommand(this));
        registerIfPresent("adminmastery",   new AdminMasteryCommand(this));
        registerIfPresent("admingodsummon", new AdminGodSummonCommand(this));
        registerIfPresent("stats",          new StatsCommand(this));
        registerIfPresent("skills",         new SkillsCommand(this));
        registerIfPresent("skillinfo",      new SkillInfoCommand(this));
        registerIfPresent("mastery",        new MasteryCommand(this));
        registerIfPresent("gem",            new GemCommand(this));
        registerIfPresent("sacrifice",      new SacrificeCommand(this));
        registerIfPresent("godsummon",      new GodSummonCommand(this));
    }

    private void registerIfPresent(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(executor);
    }

    public String prefix() {
        return getConfig().getString("messages.prefix", "§8[§cHeartsSMP§8] §r");
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    public static HeartsSMPPlugin getInstance()          { return instance; }
    public DataManager getDataManager()                   { return dataManager; }
    public GodManager getGodManager()                     { return godManager; }
    public DivineWorldManager getDivineWorldManager()     { return divineWorldManager; }
    public DivineTrialManager getDivineTrialManager()     { return divineTrialManager; }
    public GemManager getGemManager()                     { return gemManager; }
    public SkillManager getSkillManager()                 { return skillManager; }
    public ItemManager getItemManager()                   { return itemManager; }
    public SkillAbilityListener getSkillAbilityListener() { return skillAbilityListener; }
}
