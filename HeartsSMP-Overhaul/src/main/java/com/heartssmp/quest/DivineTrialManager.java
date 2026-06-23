package com.heartssmp.quest;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.data.PlayerData;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * DivineTrialManager v2 — manages the 12-castle progression system.
 *
 * Players must visit all 12 Castles of God and complete unique trials.
 * Trial completion unlocks high-tier divine items and ultimately the
 * GracefulEnlightenment skill.
 *
 * Trial types per castle:
 *   1  — Survive 60s in a storm of lightning
 *   2  — Build the divine altar pattern
 *   3  — Kill 3 divine guardians (spawned skeletons in gold armor)
 *   4  — Find the hidden beacon in the dark
 *   5  — Craft a golden torch and place it on the altar
 *   6  — Withstand poison and hunger for 30s without dying
 *   7  — Defeat a mini-boss wither skeleton
 *   8  — Solve the block-color puzzle
 *   9  — Reach the top of the spire without flight
 *  10  — Kill 10 enemies within 5 minutes
 *  11  — Stand on the beacon for 30s without moving
 *  12  — Face the God Trial: survive a 60s divine onslaught
 */
public class DivineTrialManager {

    private final HeartsSMPPlugin plugin;

    // castleVisited[playerUUID] = set of castle indices (1-12) completed
    private final Map<UUID, Set<Integer>> completedCastles = new HashMap<>();
    // Currently active trial per player
    private final Map<UUID, ActiveTrial> activeTrials = new HashMap<>();

    public DivineTrialManager(HeartsSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Trial start / management ──────────────────────────────────────────────

    /** Called when a player enters a castle's trial zone (from DivineTrialListener) */
    public void startTrial(Player player, int castleNumber) {
        UUID uuid = player.getUniqueId();
        if (completedCastles.computeIfAbsent(uuid, k -> new HashSet<>()).contains(castleNumber)) {
            player.sendMessage("§6✦ §7You have already completed §e" + getCastleName(castleNumber) + "§7.");
            return;
        }
        if (activeTrials.containsKey(uuid)) {
            player.sendMessage("§6✦ §7You already have an active trial. Complete or abandon it first.");
            return;
        }

        ActiveTrial trial = new ActiveTrial(castleNumber, System.currentTimeMillis());
        activeTrials.put(uuid, trial);

        announceTrialStart(player, castleNumber);
        launchTrialTask(player, castleNumber, trial);
    }

    private void announceTrialStart(Player player, int castleNum) {
        player.sendMessage("");
        player.sendMessage("§e§l✦ ══════════════════════════════ ✦");
        player.sendMessage("§6§l    DIVINE TRIAL — " + getCastleName(castleNum).toUpperCase());
        player.sendMessage("§7    " + getTrialDescription(castleNum));
        player.sendMessage("§e§l✦ ══════════════════════════════ ✦");
        player.sendMessage("");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.8f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0),
                80, 1, 1, 1, 0.15);
    }

    /** Core trial logic per castle number */
    private void launchTrialTask(Player player, int castleNum, ActiveTrial trial) {
        UUID uuid = player.getUniqueId();
        switch (castleNum) {
            case 1 -> trialLightningStorm(player, trial);
            case 2 -> trialAltarBuild(player, trial);
            case 3 -> trialGuardians(player, trial, 3);
            case 4 -> trialFindBeacon(player, trial);
            case 5 -> trialGoldenTorch(player, trial);
            case 6 -> trialEndurance(player, trial);
            case 7 -> trialMiniBoss(player, trial);
            case 8 -> trialPuzzle(player, trial);
            case 9 -> trialClimb(player, trial);
            case 10 -> trialKillStreak(player, trial, 10);
            case 11 -> trialStillness(player, trial);
            case 12 -> trialDivineOnslaught(player, trial);
            default -> completeTrial(player, castleNum);
        }
    }

    // ── Individual Trial Implementations ─────────────────────────────────────

    /** Trial 1: Survive 60 seconds of lightning strikes */
    private void trialLightningStorm(Player player, ActiveTrial trial) {
        player.sendMessage("§e⚡ Survive the divine storm for §660 seconds§e!");
        World world = player.getWorld();
        new BukkitRunnable() {
            int seconds = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                seconds++;
                // Strike 2 random locations near player every second
                for (int i = 0; i < 2; i++) {
                    double rx = player.getLocation().getX() + (Math.random()-0.5)*16;
                    double rz = player.getLocation().getZ() + (Math.random()-0.5)*16;
                    int hy = world.getHighestBlockYAt((int)rx, (int)rz);
                    world.strikeLightningEffect(new Location(world, rx, hy, rz));
                }
                // Boss bar / countdown message every 10s
                if (seconds % 10 == 0) player.sendMessage("§e⚡ " + (60-seconds) + "s remaining...");
                if (seconds >= 60) {
                    cancel();
                    completeTrial(player, trial.castleNumber);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Trial 2: Build the divine altar pattern (3x3 gold blocks) */
    private void trialAltarBuild(Player player, ActiveTrial trial) {
        player.sendMessage("§6⚒ Build the Divine Altar: place §69 Gold Blocks§6 in a 3x3 pattern at your feet!");
        // Give 9 gold blocks
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.GOLD_BLOCK, 9));
        player.sendMessage("§7(9 Gold Blocks added to your inventory)");
        // Poll every second to check if 3x3 of gold blocks is present
        new BukkitRunnable() {
            int timeout = 120;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                if (timeout-- <= 0) { failTrial(player, trial.castleNumber, "Time ran out!"); cancel(); return; }
                Location loc = player.getLocation();
                int y = loc.getBlockY() - 1;
                boolean complete = true;
                for (int dx = -1; dx <= 1 && complete; dx++)
                for (int dz = -1; dz <= 1 && complete; dz++)
                    if (player.getWorld().getBlockAt(loc.getBlockX()+dx, y, loc.getBlockZ()+dz).getType() != Material.GOLD_BLOCK)
                        complete = false;
                if (complete) { cancel(); completeTrial(player, trial.castleNumber); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Trial 3: Kill N divine guardians */
    private void trialGuardians(Player player, ActiveTrial trial, int count) {
        player.sendMessage("§c⚔ Defeat §e" + count + " Divine Guardians§c!");
        trial.targetKills = count;
        trial.currentKills = 0;
        World world = player.getWorld();
        // Spawn guardians as Skeletons with gold armor
        for (int i = 0; i < count; i++) {
            double angle = (2*Math.PI/count)*i;
            Location spawnLoc = player.getLocation().add(Math.cos(angle)*5, 0, Math.sin(angle)*5);
            world.spawn(spawnLoc, org.bukkit.entity.Skeleton.class, skeleton -> {
                skeleton.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.GOLDEN_HELMET));
                skeleton.getEquipment().setChestplate(new org.bukkit.inventory.ItemStack(Material.GOLDEN_CHESTPLATE));
                skeleton.setCustomName("§6§lDivine Guardian");
                skeleton.setCustomNameVisible(true);
                skeleton.addScoreboardTag("divine_guardian_" + player.getUniqueId());
                skeleton.setMaxHealth(20);
                skeleton.setHealth(20);
            });
        }
        // Completion is tracked in EntityDeathListener — see completeDivineGuardianKill()
        // Set a 5-minute timeout
        new BukkitRunnable() {
            @Override public void run() {
                if (activeTrials.containsKey(player.getUniqueId()))
                    failTrial(player, trial.castleNumber, "Time ran out!");
            }
        }.runTaskLater(plugin, 20L * 60 * 5);
    }

    /** Called by EntityDeathListener when a divine guardian is killed */
    public void onDivineGuardianKill(Player killer) {
        ActiveTrial trial = activeTrials.get(killer.getUniqueId());
        if (trial == null || trial.castleNumber != 3) return;
        trial.currentKills++;
        killer.sendMessage("§c⚔ Guardian slain! §e" + trial.currentKills + "/" + trial.targetKills);
        if (trial.currentKills >= trial.targetKills) completeTrial(killer, trial.castleNumber);
    }

    /** Trial 6: Endurance — survive poison+hunger for 30s */
    private void trialEndurance(Player player, ActiveTrial trial) {
        player.sendMessage("§2☠ Endure the divine affliction for §e30 seconds§2!");
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 20*35, 0, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HUNGER, 20*35, 2, false, true));
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                t++;
                if (t % 10 == 0) player.sendMessage("§2☠ " + (30-t) + "s remaining...");
                if (t >= 30) { cancel(); completeTrial(player, trial.castleNumber); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Trial 11: Stand still on beacon for 30s */
    private void trialStillness(Player player, ActiveTrial trial) {
        player.sendMessage("§e✦ Stand upon the beacon without moving for §a30 seconds§e!");
        final Location startLoc = player.getLocation().clone();
        new BukkitRunnable() {
            int seconds = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                double moved = player.getLocation().distanceSquared(startLoc);
                if (moved > 1.0) {
                    player.sendMessage("§c✗ You moved! Trial failed.");
                    failTrial(player, trial.castleNumber, "You moved!");
                    cancel(); return;
                }
                seconds++;
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0),
                        3, 0.3, 0.3, 0.3, 0.05);
                if (seconds % 10 == 0) player.sendMessage("§e✦ " + (30-seconds) + "s remaining...");
                if (seconds >= 30) { cancel(); completeTrial(player, trial.castleNumber); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Trial 12: Divine Onslaught — survive 60s with continuous challenges */
    private void trialDivineOnslaught(Player player, ActiveTrial trial) {
        player.sendMessage("§4§l✦ THE FINAL TRIAL. SURVIVE THE DIVINE ONSLAUGHT FOR 60 SECONDS! ✦");
        plugin.getServer().broadcastMessage("§4§l⚠ " + player.getName() + " faces the FINAL DIVINE TRIAL! ⚠");
        World world = player.getWorld();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                t++;
                // Every 5s — lightning strike near player
                if (t % 5 == 0) world.strikeLightning(player.getLocation().add(
                        (Math.random()-0.5)*8, 0, (Math.random()-0.5)*8));
                // Every 10s — spawn a guardian
                if (t % 10 == 0) {
                    world.spawn(player.getLocation().add(3,0,3), org.bukkit.entity.Skeleton.class, s -> {
                        s.setCustomName("§4§lDivine Wrath");
                        s.setCustomNameVisible(true);
                        s.setMaxHealth(30); s.setHealth(30);
                        s.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.GOLDEN_HELMET));
                    });
                }
                // Particle storm
                world.spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0),
                        10, 1, 1, 1, 0.2);
                if (t % 10 == 0) player.sendMessage("§4§l⚠ " + (60-t) + "s remaining in the Final Trial!");
                if (t >= 60) { cancel(); completeTrial(player, trial.castleNumber); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // Stub trials for remaining types
    private void trialFindBeacon(Player player, ActiveTrial trial) {
        player.sendMessage("§e🔦 Find the hidden beacon within 120 seconds!");
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                if (t++ > 120) { failTrial(player, trial.castleNumber, "Time ran out!"); cancel(); }
                // Check if player is near the beacon (set at castle center + 5 offset)
                Location beacon = plugin.getDivineWorldManager().getCastleLocation(4);
                if (beacon != null && player.getLocation().distanceSquared(beacon.add(7,0,7)) < 4) {
                    cancel(); completeTrial(player, trial.castleNumber);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void trialGoldenTorch(Player player, ActiveTrial trial) {
        player.sendMessage("§6🔥 Craft a Golden Torch (LANTERN) and place it on the altar!");
        // Simplified: give them a lantern and check placement
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.LANTERN));
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                if (t++ > 60) { failTrial(player, trial.castleNumber, "Time ran out!"); cancel(); }
                Location altar = plugin.getDivineWorldManager().getCastleLocation(5);
                if (altar != null) {
                    org.bukkit.block.Block b = altar.getWorld().getBlockAt(altar.getBlockX(), altar.getBlockY(), altar.getBlockZ());
                    if (b.getType() == Material.LANTERN) { cancel(); completeTrial(player, trial.castleNumber); }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void trialMiniBoss(Player player, ActiveTrial trial) {
        player.sendMessage("§c💀 Defeat the Divine Wither Skeleton boss!");
        trial.targetKills = 1; trial.currentKills = 0;
        player.getWorld().spawn(player.getLocation().add(5,0,5), org.bukkit.entity.WitherSkeleton.class, ws -> {
            ws.setCustomName("§4§l✦ Warden of God ✦");
            ws.setCustomNameVisible(true);
            ws.setMaxHealth(100); ws.setHealth(100);
            ws.addScoreboardTag("divine_boss_" + player.getUniqueId());
        });
    }

    private void trialPuzzle(Player player, ActiveTrial trial) {
        player.sendMessage("§9🧩 Solve the puzzle: Place WHITE, YELLOW, GOLD blocks in sequence N→E→S!");
        // Simplified check — player places blocks and position is validated
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                if (t++ > 90) { failTrial(player, trial.castleNumber, "Time ran out!"); cancel(); }
                Location base = player.getLocation();
                World w = base.getWorld();
                boolean n = w.getBlockAt(base.getBlockX(), base.getBlockY()-1, base.getBlockZ()-3).getType() == Material.WHITE_CONCRETE;
                boolean e = w.getBlockAt(base.getBlockX()+3, base.getBlockY()-1, base.getBlockZ()).getType() == Material.YELLOW_CONCRETE;
                boolean s = w.getBlockAt(base.getBlockX(), base.getBlockY()-1, base.getBlockZ()+3).getType() == Material.GOLD_BLOCK;
                if (n && e && s) { cancel(); completeTrial(player, trial.castleNumber); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void trialClimb(Player player, ActiveTrial trial) {
        player.sendMessage("§a🏔 Reach the top of the castle spire without flying! (Y+" + 30 + " above current)");
        double targetY = player.getLocation().getY() + 30;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !activeTrials.containsKey(player.getUniqueId())) { cancel(); return; }
                if (t++ > 120) { failTrial(player, trial.castleNumber, "Time ran out!"); cancel(); return; }
                if (player.isFlying()) { failTrial(player, trial.castleNumber, "No flying allowed!"); cancel(); return; }
                if (player.getLocation().getY() >= targetY) { cancel(); completeTrial(player, trial.castleNumber); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void trialKillStreak(Player player, ActiveTrial trial, int target) {
        player.sendMessage("§c⚔ Kill §e" + target + " mobs§c within 5 minutes!");
        trial.targetKills = target; trial.currentKills = 0;
        // Spawn mobs
        for (int i = 0; i < target; i++) {
            double a = (2*Math.PI/target)*i;
            player.getWorld().spawnEntity(player.getLocation().add(Math.cos(a)*8, 0, Math.sin(a)*8),
                    org.bukkit.entity.EntityType.ZOMBIE);
        }
        new BukkitRunnable() {
            @Override public void run() {
                if (activeTrials.containsKey(player.getUniqueId()))
                    failTrial(player, trial.castleNumber, "Time ran out!");
            }
        }.runTaskLater(plugin, 20L*60*5);
    }

    // ── Trial completion / failure ────────────────────────────────────────────

    public void completeTrial(Player player, int castleNumber) {
        if (!activeTrials.containsKey(player.getUniqueId())) return;
        activeTrials.remove(player.getUniqueId());
        completedCastles.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(castleNumber);

        // Rewards
        giveTrialReward(player, castleNumber);

        player.sendMessage("");
        player.sendMessage("§e§l✦ ════════════════════════════ ✦");
        player.sendMessage("§6§l    ✓ TRIAL COMPLETE — " + getCastleName(castleNumber).toUpperCase());
        player.sendMessage("§7    Castle " + castleNumber + " of 12 conquered.");
        int completed = completedCastles.get(player.getUniqueId()).size();
        player.sendMessage("§e    Progress: §a" + completed + "§e/§a12 §ecompleted.");
        player.sendMessage("§e§l✦ ════════════════════════════ ✦");
        player.sendMessage("");

        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0,1,0),
                150, 1, 1, 1, 0.3);
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        plugin.getServer().broadcastMessage(
                "§e§l✦ " + player.getName() + " §6has completed §e" + getCastleName(castleNumber) + "§6! §e(" + completed + "/12)");

        // Check if all 12 completed
        if (completed >= 12) onAllCastlesCompleted(player);
    }

    private void giveTrialReward(Player player, int castleNumber) {
        // Each castle gives a specific reward
        String itemId = switch (castleNumber) {
            case 1  -> "wind_feather";
            case 2  -> "stone_core";
            case 3  -> "soul_blade";
            case 4  -> "void_lens";
            case 5  -> "golden_torch";
            case 6  -> "life_crystal";
            case 7  -> "void_dagger";
            case 8  -> "celestia_dust";
            case 9  -> "aurora_crown";
            case 10 -> "celestial_blade";
            case 11 -> "star_fragment";
            case 12 -> "gods_trident";
            default -> "heart_shard";
        };
        plugin.getItemManager().giveItem(player, itemId);
        player.sendMessage("§6✦ §eReward: §6" + itemId.replace("_", " ").toUpperCase());
    }

    private void onAllCastlesCompleted(Player player) {
        plugin.getServer().broadcastMessage("");
        plugin.getServer().broadcastMessage("§e§l✦ ══════════════════════════════════════════ ✦");
        plugin.getServer().broadcastMessage("§6§l  ★ " + player.getName().toUpperCase() + " HAS CONQUERED ALL 12 CASTLES! ★");
        plugin.getServer().broadcastMessage("§7    The Divine acknowledges your worth. You are chosen.");
        plugin.getServer().broadcastMessage("§e§l✦ ══════════════════════════════════════════ ✦");
        plugin.getServer().broadcastMessage("");

        // Grant GracefulEnlightenment skill
        plugin.getSkillManager().grantSkill(player, "graceful_enlightenment");
        player.sendMessage("§e§l✦ You have been granted: §6§lGraceful Enlightenment §e— The Divine Skill!");

        // Award summon charges to first completer
        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        if (data != null && data.getGodSummonCharges() == 0) {
            int charges = plugin.getConfig().getInt("divine-trial.first-completer-summon-charges", 3);
            data.setGodSummonCharges(charges);
            player.sendMessage("§6✦ §eAs the first to conquer all trials, you receive §a" + charges + " §eGod Summon charges!");
        }
    }

    public void failTrial(Player player, int castleNumber, String reason) {
        activeTrials.remove(player.getUniqueId());
        player.sendMessage("§c§l✗ Trial Failed: §7" + reason);
        player.sendMessage("§c  Approach the castle again to retry " + getCastleName(castleNumber) + ".");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1.5f);
    }

    // ── Admin API ─────────────────────────────────────────────────────────────

    public void adminForceTrial(Player player, int castleNumber) {
        activeTrials.remove(player.getUniqueId());
        Location loc = plugin.getDivineWorldManager().getCastleLocation(castleNumber);
        if (loc != null) player.teleport(loc);
        startTrial(player, castleNumber);
    }

    public void adminSkipCurrentTrial(Player player) {
        ActiveTrial trial = activeTrials.get(player.getUniqueId());
        if (trial != null) completeTrial(player, trial.castleNumber);
        else player.sendMessage(plugin.prefix() + "§cNo active trial to skip.");
    }

    public void adminResetTrials(Player player) {
        activeTrials.remove(player.getUniqueId());
        completedCastles.remove(player.getUniqueId());
        player.sendMessage(plugin.prefix() + "§aAll trial progress has been reset.");
    }

    public String getTrialStatus(Player player) {
        Set<Integer> done = completedCastles.getOrDefault(player.getUniqueId(), Collections.emptySet());
        StringBuilder sb = new StringBuilder("Completed " + done.size() + "/12: ");
        for (int i = 1; i <= 12; i++) sb.append(done.contains(i) ? "§a" : "§c").append(i).append("§7 ");
        ActiveTrial active = activeTrials.get(player.getUniqueId());
        if (active != null) sb.append("\n§eCurrently in: Castle ").append(active.castleNumber);
        return sb.toString();
    }

    public boolean hasCompletedCastle(UUID uuid, int castleNumber) {
        return completedCastles.getOrDefault(uuid, Collections.emptySet()).contains(castleNumber);
    }

    private String getCastleName(int n) {
        return plugin.getDivineWorldManager().getCastleName(n);
    }

    private String getTrialDescription(int n) {
        return switch (n) {
            case 1  -> "Survive 60 seconds of divine lightning.";
            case 2  -> "Build the divine altar: 3x3 gold blocks.";
            case 3  -> "Defeat 3 Divine Guardians.";
            case 4  -> "Find the hidden beacon in the dark.";
            case 5  -> "Place a Golden Torch upon the altar.";
            case 6  -> "Withstand poison and hunger for 30 seconds.";
            case 7  -> "Defeat the Divine Wither Skeleton boss.";
            case 8  -> "Solve the sacred block-color puzzle.";
            case 9  -> "Climb the castle spire without flight.";
            case 10 -> "Kill 10 enemies within 5 minutes.";
            case 11 -> "Stand still on the beacon for 30 seconds.";
            case 12 -> "Survive the Divine Onslaught for 60 seconds.";
            default -> "Complete the trial.";
        };
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    private static class ActiveTrial {
        final int castleNumber;
        final long startTime;
        int targetKills = 0;
        int currentKills = 0;
        ActiveTrial(int castleNumber, long startTime) {
            this.castleNumber = castleNumber;
            this.startTime    = startTime;
        }
    }
}


    /** Called by GemAbilityListener for kill-streak trials (castle 10) */
    public void onMobKill(Player player) {
        ActiveTrial trial = activeTrials.get(player.getUniqueId());
        if (trial == null || trial.castleNumber != 10) return;
        trial.currentKills++;
        player.sendMessage("§c⚔ Kills: §e" + trial.currentKills + "/" + trial.targetKills);
        if (trial.currentKills >= trial.targetKills) completeTrial(player, trial.castleNumber);
    }
// ── Additional listener hooks ─────────────────────────────────────────────

