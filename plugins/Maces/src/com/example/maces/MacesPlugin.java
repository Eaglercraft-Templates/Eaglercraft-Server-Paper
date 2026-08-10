package com.example.maces;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MacesPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, Long> lastMoveTimestamps = new HashMap<>();
    private final Map<UUID, ItemStack> backpackEditTargets = new HashMap<>();
    private final Map<UUID, Integer> backpackEditSlots = new HashMap<>();
    private final Map<String, Inventory> backpackInventories = new HashMap<>();
    private final Map<String, UUID> lockedChests = new HashMap<>();
    private File backpackFile;
    private FileConfiguration backpackConfig;

    @Override
    public void onEnable() {
        registerMaceRecipe();
        registerLooterRecipe();
        registerBackpackRecipe();
        registerLockRecipe();
        getServer().getPluginManager().registerEvents(this, this);
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        backpackFile = new File(getDataFolder(), "backpacks.yml");
        backpackConfig = YamlConfiguration.loadConfiguration(backpackFile);
        loadBackpackData();
        PluginCommand maceCommand = getCommand("mace");
        if (maceCommand != null) {
            maceCommand.setExecutor(this);
        }
        PluginCommand looterCommand = getCommand("looter");
        if (looterCommand != null) {
            looterCommand.setExecutor(this);
        }
        PluginCommand backpackCommand = getCommand("backpack");
        if (backpackCommand != null) {
            backpackCommand.setExecutor(this);
        }
        PluginCommand lockCommand = getCommand("lock");
        if (lockCommand != null) {
            lockCommand.setExecutor(this);
        }
        getLogger().info("Maces plugin enabled");
    }

    private void registerMaceRecipe() {
        ItemStack mace = createMace();
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "mace"), mace);
        recipe.shape(" B ", " B ", " S ");
        recipe.setIngredient('B', Material.BEDROCK);
        recipe.setIngredient('S', Material.STICK);
        getServer().addRecipe(recipe);
    }

    private void registerLooterRecipe() {
        ItemStack looter = createLooter();
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "looter"), looter);
        recipe.shape(" D ", " E ", " O ");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('E', Material.EYE_OF_ENDER);
        recipe.setIngredient('O', Material.OBSIDIAN);
        getServer().addRecipe(recipe);
    }

    private void registerBackpackRecipe() {
        ItemStack backpack = createBackpack();
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "backpack"), backpack);
        recipe.shape("LLL", "CHC", "LLL");
        recipe.setIngredient('C', Material.CHEST);
        recipe.setIngredient('H', Material.HOPPER);
        recipe.setIngredient('L', Material.LEATHER);
        getServer().addRecipe(recipe);
    }

    private void registerLockRecipe() {
        ItemStack lock = createLock();
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "lock"), lock);
        recipe.shape("III", "IFI", "III");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('F', Material.IRON_BARS);
        getServer().addRecipe(recipe);
    }

    private ItemStack createMace() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Mace");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Strike players with this mace.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLooter() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        item.setDurability((short) 0);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "The Looter");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Hit a player who has been still for 3 seconds.");
            lore.add(ChatColor.GRAY + "It will drop their inventory and fill it with dirt.");
            meta.setLore(lore);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackpack() {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Backpack");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "[Backpack]");
            lore.add(ChatColor.GRAY + "This backpack stores its own contents.");
            lore.add(ChatColor.GRAY + "It keeps its items when dropped on death.");
            lore.add(ChatColor.GRAY + "Use /backpack open to access it.");
            meta.setLore(lore);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLock() {
        ItemStack item = new ItemStack(Material.FLINT_AND_STEEL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Lock");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right click a chest to lock it.");
            lore.add(ChatColor.GRAY + "Only the owner can open it.");
            lore.add(ChatColor.GRAY + "Use it again to unlock the chest.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack findBackpack(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        if (isBackpack(chestplate)) {
            return chestplate;
        }
        return null;
    }

    private String getBackpackId(ItemStack backpack) {
        if (backpack == null) {
            return null;
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(backpack.getType());
        }

        List<String> lore = meta == null ? null : meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "[Backpack]");
        }

        for (String line : lore) {
            if (line != null && line.contains("backpack-id:")) {
                return line.replace("backpack-id:", "").trim();
            }
        }

        String id = UUID.randomUUID().toString();
        lore.add(ChatColor.MAGIC + "backpack-id:" + id);
        meta.setLore(lore);
        backpack.setItemMeta(meta);
        return id;
    }

    private Inventory getBackpackInventory(ItemStack backpack) {
        String id = getBackpackId(backpack);
        if (id == null) {
            return Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Backpack");
        }
        if (!backpackInventories.containsKey(id)) {
            Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Backpack");
            backpackInventories.put(id, inventory);
        }
        return backpackInventories.get(id);
    }

    private void saveBackpackContents(ItemStack backpack, Inventory inventory) {
        String id = getBackpackId(backpack);
        if (id != null) {
            Inventory stored = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Backpack");
            stored.setContents(inventory.getContents());
            backpackInventories.put(id, stored);
            saveBackpackData();
        }
    }

    private void persistBackpackItem(Player player, ItemStack backpack, int slot) {
        if (slot == -2) {
            player.getInventory().setChestplate(backpack);
        } else if (slot >= 0) {
            player.getInventory().setItem(slot, backpack);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (!backpackEditTargets.containsKey(player.getUniqueId())) {
            return;
        }
        ItemStack backpack = backpackEditTargets.get(player.getUniqueId());
        if (backpack == null) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        saveBackpackContents(backpack, topInventory);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (!backpackEditTargets.containsKey(player.getUniqueId())) {
            return;
        }
        ItemStack backpack = backpackEditTargets.get(player.getUniqueId());
        if (backpack == null) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        saveBackpackContents(backpack, topInventory);
    }

    private void openBackpack(Player player) {
        ItemStack backpack = findBackpack(player);
        if (backpack == null) {
            player.sendMessage(ChatColor.RED + "You need a Backpack item equipped on your chestplate to open it.");
            return;
        }

        int slot = -2;

        Inventory inventory = getBackpackInventory(backpack);
        persistBackpackItem(player, backpack, slot);
        backpackEditTargets.put(player.getUniqueId(), backpack);
        backpackEditSlots.put(player.getUniqueId(), slot);
        player.openInventory(inventory);
    }

    private void renameBackpack(Player player, String newName) {
        ItemStack backpack = findBackpack(player);
        if (backpack == null) {
            player.sendMessage(ChatColor.RED + "You need a Backpack item equipped on your chestplate to rename it.");
            return;
        }

        ItemMeta meta = backpack.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName(ChatColor.GOLD + newName);
        List<String> lore = meta.getLore() == null ? new ArrayList<String>() : meta.getLore();
        if (lore.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "[Backpack]");
        } else {
            boolean hasMarker = false;
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("[Backpack]")) {
                    hasMarker = true;
                    break;
                }
            }
            if (!hasMarker) {
                lore.add(0, ChatColor.DARK_GRAY + "[Backpack]");
            }
        }
        meta.setLore(lore);
        backpack.setItemMeta(meta);

        persistBackpackItem(player, backpack, -2);

        player.sendMessage(ChatColor.GREEN + "Backpack renamed to " + ChatColor.GOLD + newName);
    }

    private boolean isBackpack(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() != Material.LEATHER_CHESTPLATE) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getLore() != null) {
            for (String line : meta.getLore()) {
                if (line != null && line.contains("[Backpack]")) {
                    return true;
                }
            }
        }
        return meta.hasDisplayName() && (ChatColor.GOLD + "Backpack").equals(meta.getDisplayName());
    }

    private boolean isMace(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() != Material.STICK) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && (ChatColor.GOLD + "Mace").equals(meta.getDisplayName());
    }

    private boolean isLooter(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() != Material.IRON_SWORD) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && (ChatColor.GOLD + "The Looter").equals(meta.getDisplayName());
    }

    private boolean isLock(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() != Material.FLINT_AND_STEEL) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && (ChatColor.GOLD + "Lock").equals(meta.getDisplayName());
    }

    private boolean isChest(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    private String getChestKey(Block block) {
        if (block == null) {
            return null;
        }
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Set<String> getChestLockKeys(Block block) {
        Set<String> keys = new HashSet<>();
        if (!isChest(block)) {
            return keys;
        }
        keys.add(getChestKey(block));
        BlockFace[] faces = new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };
        for (BlockFace face : faces) {
            Block neighbor = block.getRelative(face);
            if (isChest(neighbor)) {
                keys.add(getChestKey(neighbor));
            }
        }
        return keys;
    }

    private UUID getLockedChestOwner(Block block) {
        if (!isChest(block)) {
            return null;
        }
        String key = getChestKey(block);
        if (key == null) {
            return null;
        }
        return lockedChests.get(key);
    }

    private void toggleChestLock(Player player, Block block) {
        Set<String> keys = getChestLockKeys(block);
        if (keys.isEmpty()) {
            return;
        }

        UUID currentOwner = null;
        for (String key : keys) {
            if (lockedChests.containsKey(key)) {
                currentOwner = lockedChests.get(key);
                break;
            }
        }

        if (currentOwner != null) {
            if (!currentOwner.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "This chest is locked by another player.");
                return;
            }
            for (String key : keys) {
                lockedChests.remove(key);
            }
            player.sendMessage(ChatColor.GREEN + "Chest unlocked.");
            return;
        }

        for (String key : keys) {
            lockedChests.put(key, player.getUniqueId());
        }
        player.sendMessage(ChatColor.GREEN + "Chest locked for " + player.getName() + ".");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isChest(block)) {
            return;
        }
        UUID owner = getLockedChestOwner(block);
        if (owner != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "This chest is locked. Right-click it with a Lock item to unlock it.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !isChest(block)) {
            return;
        }

        if (isLock(event.getItem())) {
            event.setCancelled(true);
            toggleChestLock(event.getPlayer(), block);
            return;
        }

        UUID owner = getLockedChestOwner(block);
        if (owner != null && !owner.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "This chest is locked by another player.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();
        ItemStack held = damager.getInventory().getItemInMainHand();

        if (isMace(held)) {
            event.setCancelled(true);
            target.kickPlayer(ChatColor.RED + "You were struck by a mace!");
            damager.sendMessage(ChatColor.GREEN + "You kicked " + target.getName() + " with the mace.");
            return;
        }

        if (isLooter(held)) {
            long lastMoved = lastMoveTimestamps.getOrDefault(target.getUniqueId(), 0L);
            long now = System.currentTimeMillis();
            if (now - lastMoved >= 3000) {
                event.setCancelled(true);
                dropAndFillInventory(target);
                damager.sendMessage(ChatColor.GREEN + "You looted " + target.getName() + "! Their inventory is now full of dirt.");
                target.sendMessage(ChatColor.RED + "You have been looted! Your items were dropped and your inventory was filled with dirt.");
            } else {
                damager.sendMessage(ChatColor.RED + "The target must stand still for 3 seconds before you can use The Looter.");
            }
        }
    }

    private void dropAndFillInventory(Player target) {
        PlayerInventory inventory = target.getInventory();
        ItemStack[] storageContents = inventory.getStorageContents();
        for (int slot = 0; slot < storageContents.length; slot++) {
            ItemStack item = storageContents[slot];
            if (item != null && item.getType() != Material.AIR) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            }
            storageContents[slot] = new ItemStack(Material.DIRT);
        }
        inventory.setStorageContents(storageContents);

        ItemStack[] armorContents = inventory.getArmorContents();
        for (int slot = 0; slot < armorContents.length; slot++) {
            ItemStack armor = armorContents[slot];
            if (armor != null && armor.getType() != Material.AIR) {
                target.getWorld().dropItemNaturally(target.getLocation(), armor);
                armorContents[slot] = null;
            }
        }
        inventory.setArmorContents(armorContents);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.getFrom().toVector().equals(event.getTo().toVector())) {
            Player player = event.getPlayer();
            lastMoveTimestamps.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!backpackEditTargets.containsKey(playerId)) {
            return;
        }

        ItemStack backpack = backpackEditTargets.remove(playerId);
        Integer slot = backpackEditSlots.remove(playerId);
        saveBackpackContents(backpack, event.getInventory());

        if (slot != null) {
            if (slot == -2) {
                player.getInventory().setChestplate(backpack);
            } else if (slot >= 0 && slot < player.getInventory().getSize()) {
                player.getInventory().setItem(slot, backpack);
            }
        }
    }

    @Override
    public void onDisable() {
        saveBackpackData();
    }

    private void loadBackpackData() {
        if (backpackConfig == null) {
            return;
        }
        for (String key : backpackConfig.getKeys(false)) {
            List<Map<String, Object>> contents = (List<Map<String, Object>>) backpackConfig.getList(key);
            if (contents == null) {
                continue;
            }
            Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Backpack");
            ItemStack[] items = new ItemStack[27];
            for (int i = 0; i < contents.size() && i < items.length; i++) {
                Object raw = contents.get(i);
                if (raw instanceof Map) {
                    try {
                        items[i] = ItemStack.deserialize((Map<String, Object>) raw);
                    } catch (Exception ignored) {
                    }
                }
            }
            inventory.setContents(items);
            backpackInventories.put(key, inventory);
        }
    }

    private void saveBackpackData() {
        if (backpackConfig == null) {
            return;
        }
        for (Map.Entry<String, Inventory> entry : backpackInventories.entrySet()) {
            String key = entry.getKey();
            Inventory inventory = entry.getValue();
            List<Map<String, Object>> contents = new ArrayList<>();
            for (ItemStack item : inventory.getContents()) {
                if (item == null) {
                    contents.add(new HashMap<>());
                } else {
                    contents.add(item.serialize());
                }
            }
            backpackConfig.set(key, contents);
        }
        try {
            backpackConfig.save(backpackFile);
        } catch (IOException e) {
            getLogger().severe("Could not save backpack data: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        PlayerInventory inventory = player.getInventory();
        if (command.getName().equalsIgnoreCase("mace")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only operators can use /mace.");
                return true;
            }
            inventory.addItem(createMace());
            player.sendMessage(ChatColor.GREEN + "You were given a mace.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("looter")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only operators can use /looter.");
                return true;
            }
            inventory.addItem(createLooter());
            player.sendMessage(ChatColor.GREEN + "You were given The Looter.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("lock")) {
            inventory.addItem(createLock());
            player.sendMessage(ChatColor.GREEN + "You were given a Lock item.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("backpack")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("open")) {
                openBackpack(player);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("rename")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /backpack rename <name>");
                    return true;
                }
                StringBuilder nameBuilder = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (i > 1) {
                        nameBuilder.append(' ');
                    }
                    nameBuilder.append(args[i]);
                }
                renameBackpack(player, nameBuilder.toString());
                return true;
            }
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only operators can create backpacks with /backpack.");
                return true;
            }
            ItemStack backpack = createBackpack();
            if (inventory.getChestplate() == null) {
                inventory.setChestplate(backpack);
                player.sendMessage(ChatColor.GREEN + "You equipped a wearable backpack. Use /backpack open to access it.");
            } else {
                inventory.addItem(backpack);
                player.sendMessage(ChatColor.GREEN + "You were given a Backpack item. Use /backpack open to access its storage.");
            }
            return true;
        }

        return false;
    }
}
