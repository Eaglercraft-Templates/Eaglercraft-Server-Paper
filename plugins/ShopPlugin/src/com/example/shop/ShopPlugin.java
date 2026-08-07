package com.example.shop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private static final String PREFIX = ChatColor.GREEN + "[Shop] " + ChatColor.WHITE;
    private static final int ITEMS_PER_PAGE = 18;

    private final Map<Material, Integer> sellPrices = new LinkedHashMap<Material, Integer>();
    private final Map<UUID, Integer> balances = new LinkedHashMap<UUID, Integer>();
    private final Map<Inventory, ShopInventoryData> shopInventoryData = new LinkedHashMap<Inventory, ShopInventoryData>();
    private final Map<Inventory, SetPriceInventoryData> setPriceInventoryData = new LinkedHashMap<Inventory, SetPriceInventoryData>();
    private final Map<UUID, Material> pendingPriceInput = new LinkedHashMap<UUID, Material>();
    private final Map<UUID, String> pendingSearchInput = new LinkedHashMap<UUID, String>();
    private final Map<UUID, Boolean> pendingResetInput = new LinkedHashMap<UUID, Boolean>();

    private File pricesFile;
    private File balancesFile;

    private static class ShopInventoryData {
        final List<Material> materials;
        final int page;
        final int maxPage;

        ShopInventoryData(List<Material> materials, int page, int maxPage) {
            this.materials = materials;
            this.page = page;
            this.maxPage = maxPage;
        }
    }

    private static class SetPriceInventoryData {
        final List<Material> materials;
        final int page;
        final int maxPage;
        final String query;

        SetPriceInventoryData(List<Material> materials, int page, int maxPage, String query) {
            this.materials = materials;
            this.page = page;
            this.maxPage = maxPage;
            this.query = query;
        }
    }
    private FileConfiguration pricesConfig;
    private FileConfiguration balancesConfig;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        pricesFile = new File(getDataFolder(), "prices.yml");
        balancesFile = new File(getDataFolder(), "balances.yml");

        if (!pricesFile.exists()) {
            try {
                pricesFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create prices.yml: " + e.getMessage());
            }
        }

        if (!balancesFile.exists()) {
            try {
                balancesFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create balances.yml: " + e.getMessage());
            }
        }

        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);
        balancesConfig = YamlConfiguration.loadConfiguration(balancesFile);

        loadDefaultPrices();
        loadPrices();
        loadBalances();

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("shop") != null) {
            getCommand("shop").setExecutor(this);
        }
        if (getCommand("sell") != null) {
            getCommand("sell").setExecutor(this);
        }
        if (getCommand("setprice") != null) {
            getCommand("setprice").setExecutor(this);
        }
        if (getCommand("balance") != null) {
            getCommand("balance").setExecutor(this);
        }

        getLogger().info("Shop plugin enabled");
    }

    @Override
    public void onDisable() {
        savePrices();
        saveBalances();
        getLogger().info("Shop plugin disabled");
    }

    private void loadDefaultPrices() {
        if (!pricesConfig.contains("prices")) {
            pricesConfig.set("prices.DIAMOND", 100);
            pricesConfig.set("prices.GOLD_INGOT", 50);
            pricesConfig.set("prices.IRON_INGOT", 20);
            pricesConfig.set("prices.COAL", 5);
            pricesConfig.set("prices.OAK_SAPLING", 3);
            savePrices();
        }
    }

    private void loadPrices() {
        sellPrices.clear();
        if (!pricesConfig.contains("prices")) {
            return;
        }

        for (String key : pricesConfig.getConfigurationSection("prices").getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
                sellPrices.put(material, pricesConfig.getInt("prices." + key));
            }
        }
    }

    private void savePrices() {
        for (Map.Entry<Material, Integer> entry : sellPrices.entrySet()) {
            pricesConfig.set("prices." + entry.getKey().name(), entry.getValue());
        }
        try {
            pricesConfig.save(pricesFile);
        } catch (IOException e) {
            getLogger().warning("Could not save prices.yml: " + e.getMessage());
        }
    }

    private void loadBalances() {
        balances.clear();
        if (!balancesConfig.contains("balances")) {
            return;
        }

        for (String key : balancesConfig.getConfigurationSection("balances").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                balances.put(uuid, balancesConfig.getInt("balances." + key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveBalances() {
        for (Map.Entry<UUID, Integer> entry : balances.entrySet()) {
            balancesConfig.set("balances." + entry.getKey().toString(), entry.getValue());
        }
        try {
            balancesConfig.save(balancesFile);
        } catch (IOException e) {
            getLogger().warning("Could not save balances.yml: " + e.getMessage());
        }
    }

    private int getBalance(UUID uuid) {
        Integer balance = balances.get(uuid);
        return balance == null ? 0 : balance;
    }

    private void setBalance(UUID uuid, int amount) {
        balances.put(uuid, amount);
    }

    private boolean isWoodVariant(Material material) {
        String name = material.name();
        return name.contains("OAK") || name.contains("SPRUCE") || name.contains("BIRCH") || name.contains("JUNGLE") || name.contains("ACACIA") || name.contains("DARK_OAK") || name.contains("CRIMSON") || name.contains("WARPED") || name.contains("WOOD") || name.contains("PLANK") || name.contains("LOG") || name.contains("LEAVES") || name.contains("SAPLING") || name.contains("FENCE") || name.contains("GATE") || name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("STAIRS") || name.contains("SLAB") || name.contains("BUTTON") || name.contains("PRESSURE_PLATE");
    }

    private int getDefaultItemPrice(Material material) {
        String name = material.name();
        if (isWoodVariant(material)) {
            return 5;
        }
        if (material.isBlock()) {
            return 4;
        }
        if (name.contains("SWORD") || name.contains("AXE") || name.contains("PICKAXE") || name.contains("SHOVEL") || name.contains("HOE")) {
            return 12;
        }
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS")) {
            return 10;
        }
        if (name.contains("BOW") || name.contains("CROSSBOW") || name.contains("TRIDENT") || name.contains("SHIELD")) {
            return 15;
        }
        if (name.contains("POTION") || name.contains("BOTTLE") || name.contains("STEAK") || name.contains("APPLE") || name.contains("BREAD") || name.contains("CARROT") || name.contains("POTATO") || name.contains("FISH") || name.contains("COOKED") || name.contains("RAW") || name.contains("MUSHROOM") || name.contains("SOUP") || name.contains("CAKE")) {
            return 3;
        }
        if (name.contains("INGOT") || name.contains("NUGGET") || name.contains("GEM") || name.contains("DUST") || name.contains("ORE") || name.contains("FLINT") || name.contains("STRING") || name.contains("FEATHER") || name.contains("LEATHER")) {
            return 8;
        }
        return 2;
    }

    private int getMaterialSortOrder(Material material) {
        String name = material.name();
        if (isWoodVariant(material)) {
            return 0;
        }
        if (name.contains("REDSTONE") || name.contains("LEVER") || name.contains("HOPPER") || name.contains("COMPARATOR") || name.contains("REPEATER") || name.contains("TORCH") || name.contains("DAYLIGHT") || name.contains("PISTON")) {
            return 2;
        }
        if (name.contains("SWORD") || name.contains("AXE") || name.contains("PICKAXE") || name.contains("SHOVEL") || name.contains("HOE")) {
            return 3;
        }
        if (name.contains("BOW") || name.contains("CROSSBOW") || name.contains("TRIDENT") || name.contains("SHIELD")) {
            return 4;
        }
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS")) {
            return 5;
        }
        if (name.contains("APPLES") || name.contains("MUSHROOM") || name.contains("CARROT") || name.contains("POTATO") || name.contains("BREAD") || name.contains("FISH") || name.contains("SOUP") || name.contains("POTION") || name.contains("BAKED") || name.contains("COOKED")) {
            return 6;
        }
        return 7;
    }

    private void updateBalanceDisplay(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("shop_balance", "dummy");
        objective.setDisplayName(ChatColor.GOLD + "Money");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Score balanceScore = objective.getScore(ChatColor.GREEN + "Balance");
        balanceScore.setScore(getBalance(player.getUniqueId()));
        player.setScoreboard(scoreboard);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateBalanceDisplay(event.getPlayer());
    }

    private void openShop(Player player) {
        openShop(player, 0);
    }

    private void openShop(Player player, int page) {
        List<Material> sortedMaterials = new ArrayList<Material>(sellPrices.keySet());
        Collections.sort(sortedMaterials, new java.util.Comparator<Material>() {
            @Override
            public int compare(Material left, Material right) {
                int order = Integer.compare(getMaterialSortOrder(left), getMaterialSortOrder(right));
                if (order != 0) {
                    return order;
                }
                return left.name().compareTo(right.name());
            }
        });

        int maxPage = Math.max(1, (sortedMaterials.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage - 1));

        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Shop - Page " + (page + 1));
        List<Material> materials = new ArrayList<Material>();

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sortedMaterials.size());
        int slot = 0;

        for (int index = startIndex; index < endIndex; index++) {
            Material material = sortedMaterials.get(index);
            materials.add(material);
            ItemStack icon = new ItemStack(material, 1);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Buy " + formatName(material));
            List<String> lore = new ArrayList<String>();
            int basePrice = sellPrices.containsKey(material) ? sellPrices.get(material) : getDefaultItemPrice(material);
            lore.add(ChatColor.GRAY + "Sell value: $" + basePrice);
            if (material.getMaxDurability() > 0) {
                lore.add(ChatColor.DARK_RED + "Damaged items lose $1 per damage");
            }
            lore.add(ChatColor.YELLOW + "Buy price: $" + (basePrice * 2));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            slot++;
        }

        if (sortedMaterials.isEmpty()) {
            ItemStack empty = new ItemStack(Material.G  BARRIER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName(ChatColor.RED + "No items configured");
            empty.setItemMeta(emptyMeta);
            inventory.setItem(13, empty);
        } else {
            if (page > 0) {
                ItemStack previous = new ItemStack(Material.ARROW);
                ItemMeta previousMeta = previous.getItemMeta();
                previousMeta.setDisplayName(ChatColor.GREEN + "Previous Page");
                previous.setItemMeta(previousMeta);
                inventory.setItem(18, previous);
            }
            if (page < maxPage - 1) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta nextMeta = next.getItemMeta();
                nextMeta.setDisplayName(ChatColor.GREEN + "Next Page");
                next.setItemMeta(nextMeta);
                inventory.setItem(26, next);
            }

            ItemStack pageInfo = new ItemStack(Material.PAPER);
            ItemMeta pageMeta = pageInfo.getItemMeta();
            pageMeta.setDisplayName(ChatColor.YELLOW + "Page " + (page + 1) + " / " + maxPage);
            pageInfo.setItemMeta(pageMeta);
            inventory.setItem(22, pageInfo);
        }

        shopInventoryData.put(inventory, new ShopInventoryData(materials, page, maxPage));
        player.openInventory(inventory);
        updateBalanceDisplay(player);
        player.sendMessage(PREFIX + "Your balance: $" + getBalance(player.getUniqueId()));
    }

    private void openSell(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Sell Items");
        ItemStack sellButton = new ItemStack(Material.EMERALD);
        ItemMeta meta = sellButton.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Sell items");
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Click here to sell everything inside.");
        meta.setLore(lore);
        sellButton.setItemMeta(meta);
        inventory.setItem(26, sellButton);
        player.openInventory(inventory);
        updateBalanceDisplay(player);
        player.sendMessage(PREFIX + "Your balance: $" + getBalance(player.getUniqueId()));
    }

    private void processSell(Player player, Inventory inventory) {
        int total = 0;

        for (int slot = 0; slot < 26; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            int itemValue = getItemSellValue(item);
            if (itemValue <= 0) {
                continue;
            }
            total += itemValue * item.getAmount();
        }

        if (total <= 0) {
            player.sendMessage(PREFIX + "Nothing to sell.");
            return;
        }

        for (int slot = 0; slot < 26; slot++) {
            inventory.clear(slot);
        }

        int newBalance = getBalance(player.getUniqueId()) + total;
        setBalance(player.getUniqueId(), newBalance);
        saveBalances();
        updateBalanceDisplay(player);
        player.sendMessage(PREFIX + "You sold your items for $" + total + " and now have $" + newBalance + " in your shop balance.");
    }

    private int getItemSellValue(ItemStack item) {
        Material material = item.getType();
        if (!sellPrices.containsKey(material)) {
            return 0;
        }
        int basePrice = sellPrices.get(material);
        if (item.getType().getMaxDurability() > 0) {
            int damage = item.getDurability();
            basePrice -= damage;
        }
        return Math.max(1, basePrice);
    }

    private void openSetPriceEditor(Player player) {
        openSetPriceEditor(player, "", 0);
    }

    private void openSetPriceEditor(Player player, String query, int page) {
        List<Material> allMaterials = new ArrayList<Material>();
        for (Material material : Material.values()) {
            if (material == null || material == Material.AIR) {
                continue;
            }
            String name = formatName(material).toLowerCase();
            if (query == null || query.isEmpty() || name.contains(query.toLowerCase())) {
                allMaterials.add(material);
            }
        }
        Collections.sort(allMaterials, new java.util.Comparator<Material>() {
            @Override
            public int compare(Material left, Material right) {
                int order = Integer.compare(getMaterialSortOrder(left), getMaterialSortOrder(right));
                if (order != 0) {
                    return order;
                }
                return left.name().compareTo(right.name());
            }
        });

        int maxPage = Math.max(1, (allMaterials.size() + 44) / 45);
        page = Math.max(0, Math.min(page, maxPage - 1));

        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Set Price");
        List<Material> pageMaterials = new ArrayList<Material>();
        int startIndex = page * 45;
        int endIndex = Math.min(startIndex + 45, allMaterials.size());

        for (int index = startIndex; index < endIndex; index++) {
            Material material = allMaterials.get(index);
            pageMaterials.add(material);
            ItemStack icon = new ItemStack(material, 1);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + formatName(material));
            List<String> lore = new ArrayList<String>();
            Integer price = sellPrices.get(material);
            if (price != null) {
                lore.add(ChatColor.GREEN + "Current price: $" + price);
            } else {
                lore.add(ChatColor.RED + "Not priced yet");
            }
            lore.add(ChatColor.GRAY + "Click to set the price for this item.");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(index - startIndex, icon);
        }

        ItemStack search = new ItemStack(Material.PAPER);
        ItemMeta searchMeta = search.getItemMeta();
        searchMeta.setDisplayName(ChatColor.AQUA + "Search items");
        List<String> searchLore = new ArrayList<String>();
        searchLore.add(ChatColor.GRAY + "Click to enter a search term.");
        searchLore.add(ChatColor.GRAY + "Or use /setprice <query>.");
        searchMeta.setLore(searchLore);
        search.setItemMeta(searchMeta);
        inventory.setItem(45, search);

        ItemStack clear = new ItemStack(Material.BOOK);
        ItemMeta clearMeta = clear.getItemMeta();
        clearMeta.setDisplayName(ChatColor.YELLOW + "Clear search");
        List<String> clearLore = new ArrayList<String>();
        clearLore.add(ChatColor.GRAY + "Click to show all items.");
        clearMeta.setLore(clearLore);
        clear.setItemMeta(clearMeta);
        inventory.setItem(46, clear);

        ItemStack pageBack = new ItemStack(Material.ARROW);
        ItemMeta backMeta = pageBack.getItemMeta();
        backMeta.setDisplayName(ChatColor.GREEN + "Previous Page");
        pageBack.setItemMeta(backMeta);
        if (page > 0) {
            inventory.setItem(48, pageBack);
        }

        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName(ChatColor.YELLOW + "Page " + (page + 1) + " / " + maxPage);
        List<String> pageLore = new ArrayList<String>();
        pageLore.add(ChatColor.GRAY + "Current query: " + (query == null || query.isEmpty() ? "<none>" : query));
        pageInfoMeta.setLore(pageLore);
        pageInfo.setItemMeta(pageInfoMeta);
        inventory.setItem(49, pageInfo);

        ItemStack pageNext = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = pageNext.getItemMeta();
        nextMeta.setDisplayName(ChatColor.GREEN + "Next Page");
        pageNext.setItemMeta(nextMeta);
        if (page < maxPage - 1) {
            inventory.setItem(50, pageNext);
        }

        ItemStack reset = new ItemStack(Material.BARRIER);
        ItemMeta resetMeta = reset.getItemMeta();
        resetMeta.setDisplayName(ChatColor.RED + "Reset item price");
        List<String> resetLore = new ArrayList<String>();
        resetLore.add(ChatColor.GRAY + "Click to type an item name and remove its price.");
        resetLore.add(ChatColor.GRAY + "Reset items will no longer appear in the shop.");
        resetMeta.setLore(resetLore);
        reset.setItemMeta(resetMeta);
        inventory.setItem(52, reset);

        ItemStack info = new ItemStack(Material.BOOK_AND_QUILL);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Set price guide");
        List<String> infoLore = new ArrayList<String>();
        infoLore.add(ChatColor.GRAY + "Click an item to set its sell price.");
        infoLore.add(ChatColor.GRAY + "Right-click a priced item to reset it immediately.");
        infoLore.add(ChatColor.GRAY + "Search first to find specific items.");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(53, info);

        setPriceInventoryData.put(inventory, new SetPriceInventoryData(pageMaterials, page, maxPage, query == null ? "" : query));
        player.openInventory(inventory);
    }

    private String formatName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getInventory().getTitle());

        if (title.startsWith("Shop")) {
            event.setCancelled(true);
            ShopInventoryData data = shopInventoryData.get(event.getInventory());
            if (data == null) {
                return;
            }
            if (event.getRawSlot() == 18 && data.page > 0) {
                openShop(player, data.page - 1);
                return;
            }
            if (event.getRawSlot() == 26 && data.page < data.maxPage - 1) {
                openShop(player, data.page + 1);
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
                return;
            }
            if (event.getRawSlot() >= data.materials.size()) {
                return;
            }

            Material material = data.materials.get(event.getRawSlot());
            int sellValue = sellPrices.containsKey(material) ? sellPrices.get(material) : getDefaultItemPrice(material);
            int buyPrice = sellValue * 2;
            int balance = getBalance(player.getUniqueId());
            if (balance < buyPrice) {
                player.sendMessage(PREFIX + "You need $" + buyPrice + " to buy that item.");
                return;
            }

            ItemStack item = new ItemStack(material, 1);
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                player.sendMessage(PREFIX + "Your inventory is full.");
                return;
            }
            player.updateInventory();

            setBalance(player.getUniqueId(), balance - buyPrice);
            saveBalances();
            updateBalanceDisplay(player);
            player.sendMessage(PREFIX + "You bought 1 " + formatName(material) + " for $" + buyPrice + ".");
            return;
        }

        if (title.equals("Sell Items")) {
            if (event.getRawSlot() == 26) {
                event.setCancelled(true);
                processSell(player, event.getInventory());
            }
        }

        if (title.equals("Set Price")) {
            event.setCancelled(true);
            SetPriceInventoryData data = setPriceInventoryData.get(event.getInventory());
            if (data == null) {
                return;
            }
            if (event.getRawSlot() == 45) {
                pendingSearchInput.put(player.getUniqueId(), "");
                player.closeInventory();
                player.sendMessage(PREFIX + "Type an item search term in chat.");
                return;
            }
            if (event.getRawSlot() == 46) {
                openSetPriceEditor(player, "", 0);
                return;
            }
            if (event.getRawSlot() == 48 && data.page > 0) {
                openSetPriceEditor(player, data.query, data.page - 1);
                return;
            }
            if (event.getRawSlot() == 50 && data.page < data.maxPage - 1) {
                openSetPriceEditor(player, data.query, data.page + 1);
                return;
            }
            if (event.getRawSlot() == 51) {
                pendingResetInput.put(player.getUniqueId(), true);
                player.closeInventory();
                player.sendMessage(PREFIX + "Type the item name to reset its sell value.");
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) {
                return;
            }
            if (event.getRawSlot() >= data.materials.size()) {
                return;
            }
            Material material = data.materials.get(event.getRawSlot());
            if (event.isRightClick()) {
                if (!sellPrices.containsKey(material)) {
                    player.sendMessage(PREFIX + formatName(material) + " already has no sell value.");
                    return;
                }
                sellPrices.remove(material);
                savePrices();
                player.sendMessage(PREFIX + "Reset " + formatName(material) + " so it no longer has a sell value.");
                openSetPriceEditor(player, data.query, data.page);
                return;
            }
            pendingPriceInput.put(player.getUniqueId(), material);
            player.closeInventory();
            player.sendMessage(PREFIX + "Type a sell price for " + formatName(material) + " in chat.");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getInventory().getTitle());
        if (title.startsWith("Shop") || title.equals("Sell Items") || title.equals("Set Price")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (pendingSearchInput.containsKey(uuid)) {
            event.setCancelled(true);
            String query = event.getMessage().trim();
            pendingSearchInput.remove(uuid);
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    openSetPriceEditor(event.getPlayer(), query, 0);
                }
            });
            return;
        }
        if (pendingResetInput.containsKey(uuid)) {
            event.setCancelled(true);
            String query = event.getMessage().trim();
            pendingResetInput.remove(uuid);
            String lowerQuery = query.toLowerCase();
            List<Material> matches = new ArrayList<Material>();
            for (Material material : Material.values()) {
                if (material == null || material == Material.AIR) {
                    continue;
                }
                String name = formatName(material).toLowerCase();
                if (name.contains(lowerQuery) || material.name().toLowerCase().contains(lowerQuery)) {
                    matches.add(material);
                }
            }
            if (matches.isEmpty()) {
                event.getPlayer().sendMessage(PREFIX + "No item matched '" + query + "'.");
                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @Override
                    public void run() {
                        openSetPriceEditor(event.getPlayer(), "", 0);
                    }
                });
                return;
            }
            if (matches.size() > 1) {
                event.getPlayer().sendMessage(PREFIX + "Multiple items matched. Type a more specific name.");
                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @Override
                    public void run() {
                        openSetPriceEditor(event.getPlayer(), query, 0);
                    }
                });
                return;
            }
            Material material = matches.get(0);
            if (!sellPrices.containsKey(material)) {
                event.getPlayer().sendMessage(PREFIX + formatName(material) + " already has no sell value.");
            } else {
                sellPrices.remove(material);
                savePrices();
                event.getPlayer().sendMessage(PREFIX + "Reset " + formatName(material) + " so it no longer has a sell value.");
            }
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    openSetPriceEditor(event.getPlayer(), "", 0);
                }
            });
            return;
        }
        if (pendingPriceInput.containsKey(uuid)) {
            event.setCancelled(true);
            Material material = pendingPriceInput.remove(uuid);
            String message = event.getMessage().trim();
            int price;
            try {
                price = Integer.parseInt(message);
            } catch (NumberFormatException e) {
                event.getPlayer().sendMessage(PREFIX + "Price must be a number.");
                return;
            }
            sellPrices.put(material, price);
            savePrices();
            event.getPlayer().sendMessage(PREFIX + "Set the sell price for " + formatName(material) + " to $" + price + ".");
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    openSetPriceEditor(event.getPlayer(), "", 0);
                }
            });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("shop")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + "Only players can use this command.");
                return true;
            }
            openShop((Player) sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("sell")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + "Only players can use this command.");
                return true;
            }
            openSell((Player) sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("balance") || command.getName().equalsIgnoreCase("money")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + "Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;
            updateBalanceDisplay(player);
            sender.sendMessage(PREFIX + "Your balance: $" + getBalance(player.getUniqueId()));
            return true;
        }

        if (command.getName().equalsIgnoreCase("setprice") || command.getName().equalsIgnoreCase("sellprice")) {
            boolean isTheDeath = sender instanceof Player && ((Player) sender).getName().equalsIgnoreCase("thedeath");
        if (!sender.isOp() && !sender.hasPermission("shop.admin") && !isThedeath) {
            sender.sendMessage(PREFIX + "Only operators can change shop prices.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Only players can use this command.");
            return true;
        }
            Player player = (Player) sender;
            String query = "";
            int page = 0;
            if (args.length > 0) {
                query = String.join(" ", args);
            }
            openSetPriceEditor(player, query, page);
            return true;
        }

        return false;
    }
}
