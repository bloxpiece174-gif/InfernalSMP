package com.heartssmp.commands;

import com.heartssmp.HeartsSMPPlugin;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /godadmin — Comprehensive God Entity and Trial admin command.
 *
 * Subcommands:
 *   /godadmin spawn <25|50|75|guide> [player] — Spawn God at target player's location
 *   /godadmin despawn [player|all]             — Despawn God (specific summoner or all)
 *   /godadmin reload                           — Reload AI config / plugin config
 *   /godadmin trial force <player> <castle#>  — Force a player into a specific castle trial
 *   /godadmin trial skip <player>             — Skip current trial for player
 *   /godadmin trial reset <player>            — Reset all trial progress for player
 *   /godadmin trial status <player>           — Show trial status for player
 *   /godadmin castles generate                — Generate all 12 divine castles now
 *   /godadmin ai toggle                       — Toggle AI on/off
 *   /godadmin ai test <message>              — Test AI response (sends as console)
 */
public class GodAdminCommand implements CommandExecutor, TabCompleter {

    private final HeartsSMPPlugin plugin;

    public GodAdminCommand(HeartsSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("heartssmp.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn"   -> handleSpawn(sender, args);
            case "despawn" -> handleDespawn(sender, args);
            case "reload"  -> handleReload(sender);
            case "trial"   -> handleTrial(sender, args);
            case "castles" -> handleCastles(sender, args);
            case "ai"      -> handleAI(sender, args);
            default        -> sendHelp(sender);
        }
        return true;
    }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    private void handleSpawn(CommandSender sender, String[] args) {
        // /godadmin spawn <25|50|75|guide> [player]
        if (args.length < 2) { sender.sendMessage("§cUsage: /godadmin spawn <25|50|75|guide> [player]"); return; }

        String formArg = args[1];
        int power;
        try {
            power = Integer.parseInt(formArg);
        } catch (NumberFormatException e) {
            power = 0; // guide
        }

        Player target = null;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) { sender.sendMessage("§cPlayer not found: " + args[2]); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cSpecify a player when running from console."); return;
        }

        Location loc = target.getLocation();
        plugin.getGodManager().adminSummonGod(target, loc, power);
        sender.sendMessage(plugin.prefix() + "§aGod Entity spawned at §e" + target.getName()
                + "§a's location with form [" + formArg.toUpperCase() + "].");
    }

    // ── Despawn ───────────────────────────────────────────────────────────────

    private void handleDespawn(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            plugin.getGodManager().despawnAll();
            sender.sendMessage(plugin.prefix() + "§aAll God Entities despawned.");
            return;
        }
        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage("§cPlayer not found: " + args[1]); return; }
            plugin.getGodManager().despawnGod(target.getUniqueId());
            sender.sendMessage(plugin.prefix() + "§aDespawned God for " + target.getName() + ".");
            return;
        }
        // No argument — despawn all
        plugin.getGodManager().despawnAll();
        sender.sendMessage(plugin.prefix() + "§aAll God Entities despawned.");
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        plugin.getGodManager().reloadAIConfig();
        sender.sendMessage(plugin.prefix() + "§aPlugin config and AI configuration reloaded successfully.");
        sender.sendMessage(plugin.prefix() + "§7Provider: §e"
                + plugin.getConfig().getString("ai.provider", "anthropic")
                + " §7| Model: §e"
                + plugin.getConfig().getString("ai.model", "?")
                + " §7| AI Enabled: §e"
                + plugin.getConfig().getBoolean("ai.enabled", true));
    }

    // ── Trial ─────────────────────────────────────────────────────────────────

    private void handleTrial(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /godadmin trial <force|skip|reset|status> <player> [castle#]");
            return;
        }
        String sub    = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage("§cPlayer not found: " + args[2]); return; }

        var trialManager = plugin.getDivineTrialManager();

        switch (sub) {
            case "force" -> {
                int castleNum = args.length >= 4 ? parseInt(args[3], 1) : 1;
                trialManager.adminForceTrial(target, castleNum);
                sender.sendMessage(plugin.prefix() + "§aForced " + target.getName()
                        + " into Castle Trial #" + castleNum + ".");
            }
            case "skip" -> {
                trialManager.adminSkipCurrentTrial(target);
                sender.sendMessage(plugin.prefix() + "§aSkipped current trial for " + target.getName() + ".");
            }
            case "reset" -> {
                trialManager.adminResetTrials(target);
                sender.sendMessage(plugin.prefix() + "§aReset all trial progress for " + target.getName() + ".");
            }
            case "status" -> {
                String status = trialManager.getTrialStatus(target);
                sender.sendMessage(plugin.prefix() + "§e" + target.getName() + " trial status:");
                sender.sendMessage("§7" + status);
            }
            default -> sender.sendMessage("§cUnknown trial subcommand: " + sub);
        }
    }

    // ── Castles ───────────────────────────────────────────────────────────────

    private void handleCastles(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§cUsage: /godadmin castles generate"); return; }
        if (args[1].equalsIgnoreCase("generate")) {
            sender.sendMessage(plugin.prefix() + "§eGenerating 12 Divine Castles — this may take a moment...");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDivineWorldManager().generateAllCastles();
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(plugin.prefix() + "§a12 Divine Castles generated!"));
            });
        }
    }

    // ── AI ────────────────────────────────────────────────────────────────────

    private void handleAI(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§cUsage: /godadmin ai <toggle|test> [message]"); return; }
        switch (args[1].toLowerCase()) {
            case "toggle" -> {
                boolean current = plugin.getConfig().getBoolean("ai.enabled", true);
                plugin.getConfig().set("ai.enabled", !current);
                plugin.saveConfig();
                sender.sendMessage(plugin.prefix() + "§eAI is now " + (!current ? "§aENABLED" : "§cDISABLED") + "§e.");
            }
            case "test" -> {
                if (args.length < 3) { sender.sendMessage("§cProvide a test message."); return; }
                String testMsg = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                sender.sendMessage(plugin.prefix() + "§eSending test message to AI: §7" + testMsg);
                plugin.getGodManager().getAiClient().askAsync(
                    UUID.randomUUID(), "AdminTest", testMsg,
                    response -> sender.sendMessage("§6✦ AI Response: §e" + response),
                    err     -> sender.sendMessage("§cAI Error: " + err)
                );
            }
            default -> sender.sendMessage("§cUnknown ai subcommand.");
        }
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e§l✦ GodAdmin Commands ✦");
        sender.sendMessage("§6/godadmin spawn <25|50|75|guide> [player]");
        sender.sendMessage("§6/godadmin despawn [player|all]");
        sender.sendMessage("§6/godadmin reload");
        sender.sendMessage("§6/godadmin trial <force|skip|reset|status> <player> [castle#]");
        sender.sendMessage("§6/godadmin castles generate");
        sender.sendMessage("§6/godadmin ai <toggle|test> [message]");
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("heartssmp.admin")) return List.of();
        if (args.length == 1) return filterList(List.of("spawn","despawn","reload","trial","castles","ai"), args[0]);
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "spawn"   -> filterList(List.of("25","50","75","guide"), args[1]);
                case "despawn" -> filterList(onlinePlayerNames(), args[1]);
                case "trial"   -> filterList(List.of("force","skip","reset","status"), args[1]);
                case "castles" -> filterList(List.of("generate"), args[1]);
                case "ai"      -> filterList(List.of("toggle","test"), args[1]);
                default        -> List.of();
            };
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("trial"))) {
            return filterList(onlinePlayerNames(), args[2]);
        }
        return List.of();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private List<String> filterList(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        return result;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
