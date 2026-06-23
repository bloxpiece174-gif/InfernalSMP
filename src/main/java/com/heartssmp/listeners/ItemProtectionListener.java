package com.heartssmp.listeners;

import com.heartssmp.HeartsSMPPlugin;
import com.heartssmp.managers.ItemManager;
import org.bukkit.Material;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * ItemProtectionListener — enforces bound item rules:
 *   • Non-droppable (Q key / death drop suppressed)
 *   • Non-placeable
 *   • Cannot be moved to other players
 *   • Passive effects require holding in main hand
 */
public class ItemProtectionListener implements Listener {

    private final HeartsSMPPlugin plugin;
    private final ItemManager itemManager;

    public ItemProtectionListener(HeartsSMPPlugin plugin) {
        this.plugin      = plugin;
        this.itemManager = plugin.getItemManager();
    }

    /** Prevent dropping bound items */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (itemManager.isBoundItem(dropped)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✦ Bound items cannot be dropped!");
        }
    }

    /** Prevent placing custom items as blocks (e.g. lantern, pumpkin base items) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (itemManager.isBoundItem(inHand)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✦ This divine item cannot be placed!");
        }
    }

    /** Prevent moving bound items in inventory (trading, hoppers through chests) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && itemManager.isBoundItem(event.getCurrentItem())) {
            // Allow moving within the player's own inventory, but block chest/crafting/trade moves
            if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER &&
                event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
        if (event.getCursor() != null && itemManager.isBoundItem(event.getCursor())) {
            if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER &&
                event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
    }

    /** Prevent death drops of bound items */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> itemManager.isBoundItem(item));
    }

    /** Prevent hoppers or dispensers picking up bound items */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player)) {
            ItemStack item = event.getItem().getItemStack();
            if (itemManager.isBoundItem(item)) {
                event.setCancelled(true);
            }
        }
    }

    /** Prevent using bound items in crafting */
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
