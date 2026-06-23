package com.heartssmp.listeners;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.managers.ItemManager;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class ItemProtectionListener implements Listener {

    private final HeartsSMPPlugin plugin;
    private final ItemManager itemManager;

    public ItemProtectionListener(HeartsSMPPlugin plugin) {
        this.plugin      = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (itemManager.isBoundItem(dropped)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✦ Bound items cannot be dropped!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (itemManager.isBoundItem(inHand)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✦ This divine item cannot be placed!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && itemManager.isBoundItem(event.getCurrentItem())) {
            if (event.getInventory().getType() != InventoryType.PLAYER
                    && event.getInventory().getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
        if (event.getCursor() != null && itemManager.isBoundItem(event.getCursor())) {
            if (event.getInventory().getType() != InventoryType.PLAYER
                    && event.getInventory().getType() != InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> itemManager.isBoundItem(item));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player)) {
            if (itemManager.isBoundItem(event.getItem().getItemStack())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && itemManager.isBoundItem(ingredient)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof org.bukkit.entity.Player p) {
                    p.sendMessage("§c✦ Bound items cannot be used in crafting!");
                }
                return;
            }
        }
    }
}
