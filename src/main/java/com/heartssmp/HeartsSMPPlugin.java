package com.heartssmp;

import com.heartssmp.ai.GodAIClient;
import com.heartssmp.commands.*;
import com.heartssmp.data.DataManager;
import com.heartssmp.gems.GemManager;
import com.heartssmp.god.*;
import com.heartssmp.listeners.*;
import com.heartssmp.managers.ItemManager;
import com.heartssmp.quest.DivineTrialManager;
import com.heartssmp.skills.SkillManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * HeartsSMPPlugin v2 — Main plugin class.
 *
 * Architecture overview:
 *   DataManager         — Loads/saves PlayerData (hearts, lives, skills, gems, trial progress)
 *   GodManager          — Manages God NPC entities + routes AI client
 *   GodAIClient         — Async HTTP client for AI provider (Anthropic/OpenAI/Gemini)
 *   DivineWorldManager  — Procedural castle generation for the Divine Dimension
 *   DivineTrialManager  — 12-castle trial progression + rewards
 *   GemManager          — All gem instances + passive tick dispatching
 *   SkillManager        — All skill instances + divine lance tracking
 *   ItemManager         — Custom item creation with CustomModelData
 *
 * Listeners registered:
 *   ItemProtectionListener  — Bound item rules (no drop/place/craft/death-drop)
 *   GemAbilityListener      — Gem right-click + Stone Gem throw/barrier
 *   SkillAbilityListener    — Skill right-click + passive ticks + Lance tracking
 *   (God listeners handled inside GodManager)
 */
public class HeartsSMPPlugin extends JavaPlugin {

    private static HeartsSMPPlugin instance;

    private DataManager       dataManager;
    private GodManager        godManager;
    private DivineWorldManager divineWorldManager;
    private DivineTrialManager divineTrialManager;
    private GemManager        gemManager;
    private SkillManager      skillManager;
    private ItemManager       itemManager;
    private SkillAbilityListener skillAbilityListener;

    @Override
    public void onEnable() {
        instance = this;

        // Config
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        // Core managers
        dataManager        = new DataManager(this);
        itemManager        = new ItemManager(this);
        gemManager         = new GemManager(this);
        skillManager       = new SkillManager(this);
        divineWorldManager = new DivineWorldManager(this);
        divineTrialManager = new DivineTrialManager(this);
        godManager         = new GodManager(this); // also registers its own listeners

        // Listeners
        skillAbilityListener = new SkillAbilityListener(this);
        getServer().getPluginManager().registerEvents(new ItemProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GemAbilityListener(this),    this);

        // Commands
        registerCommands();

        // Passive tick task — every 4 ticks (0.2s) dispatch gem/skill passives
        new BukkitRunnable() {
            @Override public void run() {
                for (var player : getServer().getOnlinePlayers()) {
                    // Only dispatch if player is alive and not in spectator
                    if (player.isDead()) continue;
                    gemManager.dispatchPassiveTick(player);
                    skillAbilityListener.dispatchPassiveTick(player);
                }
            }
        }.runTaskTimer(this, 20L, 4L);

        // Data save task every 5 minutes
        new BukkitRunnable() {
            @Override public void run() { dataManager.saveAll(); }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L);

        getLogger().info("╔════════════════════════════════════╗");
        getLogger().info("║  HeartsSMP v2.0 — FULLY LOADED     ║");
        getLogger().info("║  God NPC: Paper PlayerProfile skin  ║");
        getLogger().info("║  AI: " + padRight(getConfig().getString("ai.provider","anthropic").toUpperCase() + " / " + getConfig().getString("ai.model","?"), 27) + "║");
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
        // Admin command
        var godAdminCmd = getCommand("godadmin");
        if (godAdminCmd != null) {
            GodAdminCommand gadmin = new GodAdminCommand(this);
            godAdminCmd.setExecutor(gadmin);
            godAdminCmd.setTabCompleter(gadmin);
        }

        // Forward remaining commands to existing command classes
        registerIfPresent("adminhearts",   new AdminHeartsCommand(this));
        registerIfPresent("adminlives",    new AdminLivesCommand(this));
        registerIfPresent("admingem",      new AdminGemCommand(this));
        registerIfPresent("adminskill",    new AdminSkillCommand(this));
        registerIfPresent("adminitem",     new AdminItemCommand(this));
        registerIfPresent("adminunban",    new AdminUnbanCommand(this));
        registerIfPresent("adminmastery",  new AdminMasteryCommand(this));
        registerIfPresent("admingodsummon",new AdminGodSummonCommand(this));
        registerIfPresent("stats",         new StatsCommand(this));
        registerIfPresent("skills",        new SkillsCommand(this));
        registerIfPresent("skillinfo",     new SkillInfoCommand(this));
        registerIfPresent("mastery",       new MasteryCommand(this));
        registerIfPresent("gem",           new GemCommand(this));
        registerIfPresent("sacrifice",     new SacrificeCommand(this));
        registerIfPresent("godsummon",     new GodSummonCommand(this));
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

    // ── Getters ───────────────────────────────────────────────────────────────

    public static HeartsSMPPlugin getInstance()         { return instance; }
    public DataManager getDataManager()                  { return dataManager; }
    public GodManager getGodManager()                    { return godManager; }
    public DivineWorldManager getDivineWorldManager()    { return divineWorldManager; }
    public DivineTrialManager getDivineTrialManager()    { return divineTrialManager; }
    public GemManager getGemManager()                    { return gemManager; }
    public SkillManager getSkillManager()                { return skillManager; }
    public ItemManager getItemManager()                  { return itemManager; }
    public SkillAbilityListener getSkillAbilityListener(){ return skillAbilityListener; }
}
