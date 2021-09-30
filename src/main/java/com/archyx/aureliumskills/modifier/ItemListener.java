package com.archyx.aureliumskills.modifier;

import com.archyx.aureliumskills.AureliumSkills;
import com.archyx.aureliumskills.configuration.Option;
import com.archyx.aureliumskills.configuration.OptionL;
import com.archyx.aureliumskills.data.PlayerData;
import com.archyx.aureliumskills.data.PlayerDataLoadEvent;
import com.archyx.aureliumskills.requirement.Requirements;
import com.archyx.aureliumskills.skills.foraging.ForagingAbilities;
import com.archyx.aureliumskills.skills.mining.MiningAbilities;
import com.archyx.aureliumskills.stats.Stat;
import com.archyx.aureliumskills.stats.StatLeveler;
import com.archyx.aureliumskills.stats.Stats;
import com.archyx.aureliumskills.util.item.ItemUtils;
import com.archyx.aureliumskills.util.version.VersionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ItemListener implements Listener {

    private final AureliumSkills plugin;
    private final Map<Player, ItemStack> heldItems;
    private final Map<Player, ItemStack> offHandItems;
    private final ForagingAbilities foragingAbilities;
    private final MiningAbilities miningAbilities;
    private final StatLeveler statLeveler;
    private final Modifiers modifiers;
    private final Requirements requirements;
    private final Multipliers multipliers;

    public ItemListener(AureliumSkills plugin) {
        this.plugin = plugin;
        heldItems = new HashMap<>();
        offHandItems = new HashMap<>();
        this.foragingAbilities = new ForagingAbilities(plugin);
        this.miningAbilities = new MiningAbilities(plugin);
        this.statLeveler = new StatLeveler(plugin);
        this.modifiers = new Modifiers(plugin);
        this.requirements = new Requirements(plugin);
        this.multipliers = new Multipliers(plugin);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerDataLoadEvent event) {
        Player player = event.getPlayerData().getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        heldItems.put(player, held);
        PlayerData playerData = event.getPlayerData();
        if (!held.getType().equals(Material.AIR)) {
            if (OptionL.getBoolean(Option.MODIFIER_AUTO_CONVERT_FROM_LEGACY)) {
                held = requirements.convertFromLegacy(modifiers.convertFromLegacy(held));
                if (!held.equals(player.getInventory().getItemInMainHand())) {
                    player.getInventory().setItemInMainHand(held);
                }
            }
            for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, held)) {
                playerData.addStatModifier(modifier, false);
            }
            for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, held)) {
                playerData.addMultiplier(multiplier);
            }
        }
        if (OptionL.getBoolean(Option.MODIFIER_ITEM_ENABLE_OFF_HAND)) {
            ItemStack offHandItem = player.getInventory().getItemInOffHand();
            offHandItems.put(player, offHandItem);
            if (!offHandItem.getType().equals(Material.AIR)) {
                if (OptionL.getBoolean(Option.MODIFIER_AUTO_CONVERT_FROM_LEGACY)) {
                    offHandItem = requirements.convertFromLegacy(modifiers.convertFromLegacy(offHandItem));
                    if (!offHandItem.equals(player.getInventory().getItemInOffHand())) {
                        player.getInventory().setItemInOffHand(offHandItem);
                    }
                }
                for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, offHandItem)) {
                    StatModifier offHandModifier = new StatModifier(modifier.getName() + ".Offhand", modifier.getStat(), modifier.getValue());
                    playerData.addStatModifier(offHandModifier);
                }
                for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, offHandItem)) {
                    Multiplier offHandMultiplier = new Multiplier(multiplier.getName() + ".Offhand", multiplier.getSkill(), multiplier.getValue());
                    playerData.addMultiplier(offHandMultiplier);
                }
            }
        }

    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        heldItems.remove(player);
    }

    public void scheduleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // For every player
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Gets stored and held items
                    ItemStack stored = heldItems.get(player);
                    ItemStack held = player.getInventory().getItemInMainHand();
                    // If stored item is not null
                    if (stored != null) {
                        // If stored item is different than held
                        if (!stored.equals(held)) {
                            Set<Stat> statsToReload = new HashSet<>();
                            // Remove modifiers from stored item
                            if (!stored.getType().equals(Material.AIR)) {
                                fixItemAttributeModifierHealth(player, stored);
                                if (hasHealthAttributeModifier(stored)) {
                                    statsToReload.add(Stats.HEALTH);
                                }
                                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                                if (playerData != null) {
                                    for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, stored)) {
                                        playerData.removeStatModifier(modifier.getName(), false);
                                        statsToReload.add(modifier.getStat());
                                    }
                                    for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, stored)) {
                                        playerData.removeMultiplier(multiplier.getName());
                                    }
                                    // Remove valor
                                    if (ItemUtils.isAxe(stored.getType())) {
                                        foragingAbilities.removeValor(playerData);
                                    }
                                    // Remove stamina
                                    if (ItemUtils.isPickaxe(stored.getType())) {
                                        miningAbilities.removeStamina(playerData);
                                    }
                                }
                            }
                            // Add modifiers from held item
                            if (!held.getType().equals(Material.AIR)) {
                                if (hasHealthAttributeModifier(held)) {
                                    statsToReload.add(Stats.HEALTH);
                                }
                                if (OptionL.getBoolean(Option.MODIFIER_AUTO_CONVERT_FROM_LEGACY)) {
                                    held = requirements.convertFromLegacy(modifiers.convertFromLegacy(held));
                                    if (!held.equals(player.getInventory().getItemInMainHand())) {
                                        player.getInventory().setItemInMainHand(held);
                                    }
                                }
                                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                                if (playerData != null) {
                                    if (requirements.meetsRequirements(ModifierType.ITEM, held, player)) {
                                        for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, held)) {
                                            playerData.addStatModifier(modifier, false);
                                            statsToReload.add(modifier.getStat());
                                        }
                                        for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, held)) {
                                            playerData.addMultiplier(multiplier);
                                        }
                                    }
                                    // Apply valor
                                    if (ItemUtils.isAxe(held.getType())) {
                                        foragingAbilities.applyValor(playerData);
                                    }
                                    // Apply stamina
                                    if (ItemUtils.isPickaxe(held.getType())) {
                                        miningAbilities.applyStamina(playerData);
                                    }
                                }
                            }
                            for (Stat stat : statsToReload) {
                                statLeveler.reloadStat(player, stat);
                            }
                            // Set stored item to held item
                            heldItems.put(player, held.clone());
                        }
                    }
                    // If no mapping exists, add held item
                    else {
                        heldItems.put(player, held.clone());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, OptionL.getInt(Option.MODIFIER_ITEM_CHECK_PERIOD));
        scheduleOffHandTask();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!event.isCancelled()) { // Make sure event is not cancelled
            if (OptionL.getBoolean(Option.MODIFIER_ITEM_ENABLE_OFF_HAND)) { // Check off hand support is enabled
                Player player = event.getPlayer();
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                if (playerData != null) {
                    // Get items switched
                    ItemStack itemOffHand = event.getOffHandItem();
                    ItemStack itemMainHand = event.getMainHandItem();
                    // Update items
                    offHandItems.put(player, itemOffHand);
                    heldItems.put(player, itemMainHand);
                    // Things to prevent double reloads
                    Set<String> offHandModifiers = new HashSet<>();
                    Set<Stat> statsToReload = new HashSet<>();
                    Set<String> offHandMultipliers = new HashSet<>();
                    // Check off hand item
                    if (itemOffHand != null) {
                        if (itemOffHand.getType() != Material.AIR) {
                            boolean meetsRequirements = requirements.meetsRequirements(ModifierType.ITEM, itemOffHand, player); // Get whether player meets requirements
                            // For each modifier on the item
                            for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, itemOffHand)) {
                                // Removes the old modifier from main hand
                                StatModifier offHandModifier = new StatModifier(modifier.getName() + ".Offhand", modifier.getStat(), modifier.getValue());
                                playerData.removeStatModifier(modifier.getName(), false);
                                // Add new one if meets requirements
                                if (meetsRequirements) {
                                    playerData.addStatModifier(offHandModifier, false);
                                }
                                // Reload check stuff
                                offHandModifiers.add(offHandModifier.getName());
                                statsToReload.add(modifier.getStat());
                            }
                            for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, itemOffHand)) {
                                Multiplier offHandMultiplier = new Multiplier(multiplier.getName() + ".Offhand", multiplier.getSkill(), multiplier.getValue());
                                playerData.removeMultiplier(multiplier.getName());
                                if (meetsRequirements) {
                                    playerData.addMultiplier(offHandMultiplier);
                                }
                                offHandMultipliers.add(offHandMultiplier.getName());
                            }
                        }
                    }
                    // Check main hand item
                    if (itemMainHand != null) {
                        if (itemMainHand.getType() != Material.AIR) {
                            boolean meetsRequirements = requirements.meetsRequirements(ModifierType.ITEM, itemMainHand, player); // Get whether player meets requirements
                            // For each modifier on the item
                            for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, itemMainHand)) {
                                // Removes the offhand modifier if wasn't already added
                                if (!offHandModifiers.contains(modifier.getName() + ".Offhand")) {
                                    playerData.removeStatModifier(modifier.getName() + ".Offhand", false);
                                }
                                // Add if meets requirements
                                if (meetsRequirements) {
                                    playerData.addStatModifier(modifier, false);
                                }
                                // Reload check stuff
                                statsToReload.add(modifier.getStat());
                            }
                            for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, itemMainHand)) {
                                if (!offHandMultipliers.contains(multiplier.getName() + ".Offhand")) {
                                    playerData.removeMultiplier(multiplier.getName() + ".Offhand");
                                }
                                if (meetsRequirements) {
                                    playerData.addMultiplier(multiplier);
                                }
                            }
                        }
                    }
                    // Reload stats
                    for (Stat stat : statsToReload) {
                        statLeveler.reloadStat(player, stat);
                    }
                }
            }
        }
    }

    public void scheduleOffHandTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (OptionL.getBoolean(Option.MODIFIER_ITEM_ENABLE_OFF_HAND)) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // Gets stored and held items
                        ItemStack stored = offHandItems.get(player);
                        ItemStack held = player.getInventory().getItemInOffHand();
                        if (stored != null) {
                            // If stored item is different than held
                            if (!stored.equals(held)) {
                                //Remove modifiers from stored item
                                if (!stored.getType().equals(Material.AIR)) {
                                    PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                                    if (playerData != null) {
                                        for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, stored)) {
                                            playerData.removeStatModifier(modifier.getName() + ".Offhand");
                                        }
                                        for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, stored)) {
                                            playerData.removeMultiplier(multiplier.getName() + ".Offhand");
                                        }
                                    }
                                }
                                // Add modifiers from held item
                                if (!held.getType().equals(Material.AIR)) {
                                    PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                                    if (playerData != null) {
                                        if (requirements.meetsRequirements(ModifierType.ITEM, held, player)) {
                                            for (StatModifier modifier : modifiers.getModifiers(ModifierType.ITEM, held)) {
                                                StatModifier offHandModifier = new StatModifier(modifier.getName() + ".Offhand", modifier.getStat(), modifier.getValue());
                                                playerData.addStatModifier(offHandModifier);
                                            }
                                            for (Multiplier multiplier : multipliers.getMultipliers(ModifierType.ITEM, held)) {
                                                Multiplier offHandMultiplier = new Multiplier(multiplier.getName() + ".Offhand", multiplier.getSkill(), multiplier.getValue());
                                                playerData.addMultiplier(offHandMultiplier);
                                            }
                                        }
                                    }
                                }
                                // Set stored item to held item
                                offHandItems.put(player, held.clone());
                            }
                        }
                        // If no mapping exists, add held item
                        else {
                            offHandItems.put(player, held.clone());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, OptionL.getInt(Option.MODIFIER_ITEM_CHECK_PERIOD));
    }

    // Checks whether an item has a vanilla health attribute modifier
    private boolean hasHealthAttributeModifier(ItemStack item) {
        if (VersionUtils.isAtLeastVersion(14)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasAttributeModifiers()) {
                Collection<AttributeModifier> attributeModifiers = meta.getAttributeModifiers(Attribute.GENERIC_MAX_HEALTH);
                return attributeModifiers != null && attributeModifiers.size() > 0;
            }
        }
        return false;
    }

    // Fixes health being above max health when a vanilla attribute modifier is removed
    private void fixItemAttributeModifierHealth(Player player, ItemStack item) {
        if (VersionUtils.isAtLeastVersion(14)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasAttributeModifiers()) {
                Collection<AttributeModifier> attributeModifiers = meta.getAttributeModifiers(Attribute.GENERIC_MAX_HEALTH);
                if (attributeModifiers != null && attributeModifiers.size() > 0) {
                    AttributeInstance attributeInstance = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (attributeInstance != null) {
                        double maxHealth = attributeInstance.getValue();
                        if (player.getHealth() > maxHealth) {
                            player.setHealth(maxHealth);
                        }
                    }
                }
            }
        }
    }

}
