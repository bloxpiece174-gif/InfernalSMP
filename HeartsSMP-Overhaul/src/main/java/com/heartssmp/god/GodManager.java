package com.heartssmp.god;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.ai.GodAIClient;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.*;

/**
 * GodManager v2 — manages all active GodEntity instances, routes player
 * chat/interaction to the correct entity, and enforces invulnerability.
 */
public class GodManager implements Listener {

    private final HeartsSMPPlugin plugin;
    private final GodAIClient aiClient;
    private final Map<UUID, GodEntity> activeGods = new HashMap<>();
    private GodEntity guideGod = null;

    public GodManager(HeartsSMPPlugin plugin) {
        this.plugin   = plugin;
        this.aiClient = new GodAIClient(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Spawn / Despawn API ───────────────────────────────────────────────────

    public void summonGod(Player summoner, Location loc, GodEntity.GodForm form) {
        despawnGod(summoner.getUniqueId());
        GodEntity god = new GodEntity(plugin, aiClient, loc, form, summoner.getUniqueId());
        god.spawn();
        activeGods.put(summoner.getUniqueId(), god);
    }

    public void summonGuideGod(Location loc, UUID summoner) {
        if (guideGod != null) guideGod.despawn();
        guideGod = new GodEntity(plugin, aiClient, loc, GodEntity.GodForm.GUIDE, summoner);
        guideGod.spawn();
    }

    public void despawnGuideGod() {
        if (guideGod != null) { guideGod.despawn(); guideGod = null; }
    }

    public void despawnGod(UUID summonerUUID) {
        GodEntity existing = activeGods.remove(summonerUUID);
        if (existing != null) existing.despawn();
    }

    public boolean hasActiveGod(UUID summonerUUID) {
        GodEntity g = activeGods.get(summonerUUID);
        return g != null && g.isActive();
    }

    public void adminSummonGod(Player target, Location loc, int powerPercent) {
        GodEntity.GodForm form = switch (powerPercent) {
            case 25 -> GodEntity.GodForm.POWER_25;
            case 50 -> GodEntity.GodForm.POWER_50;
            case 75 -> GodEntity.GodForm.POWER_75;
            default -> GodEntity.GodForm.GUIDE;
        };
        summonGod(target, loc, form);
    }

    public void despawnAll() {
        activeGods.values().forEach(GodEntity::despawn);
        activeGods.clear();
        despawnGuideGod();
    }

    public GodEntity.GodForm getNextGodForm(int summonsUsed) {
        return switch (summonsUsed) {
            case 0  -> GodEntity.GodForm.POWER_25;
            case 1  -> GodEntity.GodForm.POWER_50;
            case 2  -> GodEntity.GodForm.POWER_75;
            default -> GodEntity.GodForm.POWER_75;
        };
    }

    /** Reload the AI client config (api key / model changes) */
    public void reloadAIConfig() {
        aiClient.clearAllHistory();
        plugin.reloadConfig();
        plugin.getLogger().info("[HeartsSMP] AI configuration reloaded.");
    }

    // ── Event Listeners ───────────────────────────────────────────────────────

    /** Route chat to any active God entity within range */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String msg    = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (GodEntity god : new ArrayList<>(activeGods.values())) {
                if (god.isActive()) {
                    god.handlePlayerInteraction(player, msg);
                    return;
                }
            }
            if (guideGod != null && guideGod.isActive()) {
                guideGod.handlePlayerInteraction(player, msg);
            }
        });
    }

    /** Right-click on God entity triggers AI interaction */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Player player  = event.getPlayer();
        for (GodEntity god : new ArrayList<>(activeGods.values())) {
            if (god.isGodEntity(clicked)) {
                event.setCancelled(true);
                god.handlePlayerInteraction(player, "I right-clicked you, O Divine One. What wisdom do you have for me?");
                return;
            }
        }
        if (guideGod != null && guideGod.isGodEntity(clicked)) {
            event.setCancelled(true);
            guideGod.handlePlayerInteraction(player, "I seek your guidance, O Divine.");
        }
    }

    /** Make God entity completely invulnerable to all damage sources */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (isGodEntity(entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (isGodEntity(entity)) {
            event.setCancelled(true);
            // Inform the attacker
            if (event.getDamager() instanceof Player attacker) {
                attacker.sendMessage("§c✦ You cannot harm the Divine. Such insolence...");
                // God reacts to being attacked (if AI enabled, log it)
                for (GodEntity god : activeGods.values()) {
                    if (god.isGodEntity(entity)) {
                        god.handlePlayerInteraction(attacker, "*attacks the God*");
                        return;
                    }
                }
            }
        }
    }

    private boolean isGodEntity(Entity e) {
        for (GodEntity god : activeGods.values()) {
            if (god.isGodEntity(e)) return true;
        }
        return guideGod != null && guideGod.isGodEntity(e);
    }

    public GodAIClient getAiClient() { return aiClient; }
}
