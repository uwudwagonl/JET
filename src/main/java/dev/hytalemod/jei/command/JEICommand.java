package dev.hytalemod.jei.command;

import dev.hytalemod.jei.HytaleJEIPlugin;
import dev.hytalemod.jei.registry.ItemInfo;

import java.util.List;


public class JEICommand {
    
    private final HytaleJEIPlugin plugin;
    
    public JEICommand(HytaleJEIPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Execute the command
     * 
     * TODO: Adapt signature to actual Hytale command API
     */
    public void execute(Object sender, String[] args) {
        if (args.length == 0) {
            openScreen(sender);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "search":
            case "s":
                handleSearch(sender, args);
                break;
                
            case "info":
            case "i":
                handleInfo(sender, args);
                break;
                
            case "reload":
            case "r":
                handleReload(sender);
                break;
                
            case "list":
            case "l":
                handleList(sender, args);
                break;
                
            case "help":
            case "?":
                showHelp(sender);
                break;
                
            default:
                // Treat as search query
                String query = String.join(" ", args);
                plugin.openJEIScreenWithSearch(sender, query);
        }
    }
    
    private void openScreen(Object sender) {
        plugin.openJEIScreen(sender);
        sendMessage(sender, "§aOpening JEI...");
    }
    
    private void handleSearch(Object sender, String[] args) {
        if (args.length < 2) {
            plugin.openJEIScreen(sender);
            return;
        }
        
        StringBuilder query = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) query.append(" ");
            query.append(args[i]);
        }
        
        plugin.openJEIScreenWithSearch(sender, query.toString());
    }
    
    private void handleInfo(Object sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "§cUsage: /jei info <item_id>");
            return;
        }
        
        String itemId = args[1];
        ItemInfo item = plugin.getItemRegistry().getItem(itemId);
        
        // Try with hytale: prefix
        if (item == null) {
            item = plugin.getItemRegistry().getItem("hytale:" + itemId);
        }
        
        if (item == null) {
            sendMessage(sender, "§cItem not found: " + itemId);
            return;
        }
        
        // Display item info
        sendMessage(sender, "§6=== " + item.getName() + " ===");
        sendMessage(sender, "§7ID: §f" + item.getId());
        sendMessage(sender, "§7Category: §f" + item.getCategory());
        sendMessage(sender, "§7Max Stack: §f" + item.getMaxStackSize());
        
        if (!item.getDescription().isEmpty()) {
            sendMessage(sender, "§7Description: §f" + item.getDescription());
        }
        
        if (!item.getTags().isEmpty()) {
            sendMessage(sender, "§7Tags: §f" + String.join(", ", item.getTags()));
        }
    }
    
    private void handleList(Object sender, String[] args) {
        String category = args.length > 1 ? args[1] : null;
        
        List<ItemInfo> items;
        if (category != null) {
            items = plugin.getItemRegistry().getItemsByCategory(category);
            if (items.isEmpty()) {
                sendMessage(sender, "§cNo items found in category: " + category);
                sendMessage(sender, "§7Available categories: " + 
                    String.join(", ", plugin.getItemRegistry().getCategories()));
                return;
            }
            sendMessage(sender, "§6=== Items in '" + category + "' ===");
        } else {
            items = plugin.getItemRegistry().getAllItems();
            sendMessage(sender, "§6=== All Items (" + items.size() + ") ===");
        }
        
        // Show first 10 items
        int shown = Math.min(items.size(), 10);
        for (int i = 0; i < shown; i++) {
            ItemInfo item = items.get(i);
            sendMessage(sender, "§7- §f" + item.getName() + " §8(" + item.getId() + ")");
        }
        
        if (items.size() > 10) {
            sendMessage(sender, "§7... and " + (items.size() - 10) + " more. Use /jei to browse all.");
        }
    }
    
    private void handleReload(Object sender) {
        sendMessage(sender, "§eReloading item registry...");
        
        long start = System.currentTimeMillis();
        plugin.getItemRegistry().scanAllItems();
        long elapsed = System.currentTimeMillis() - start;
        
        int count = plugin.getItemRegistry().getItemCount();
        sendMessage(sender, "§aReload complete! Found " + count + " items in " + elapsed + "ms.");
    }
    
    private void showHelp(Object sender) {
        sendMessage(sender, "§6=== HytaleJEI Commands ===");
        sendMessage(sender, "§e/jei §7- Open the item browser");
        sendMessage(sender, "§e/jei search <query> §7- Search for items");
        sendMessage(sender, "§e/jei info <item_id> §7- Show item details");
        sendMessage(sender, "§e/jei list [category] §7- List items");
        sendMessage(sender, "§e/jei reload §7- Rescan registries");
        sendMessage(sender, "");
        sendMessage(sender, "§6Search Tips:");
        sendMessage(sender, "§7  @category §f- Filter by category (e.g., @tools)");
        sendMessage(sender, "§7  #tag §f- Filter by tag (e.g., #ore)");
    }
    
    /**
     * Tab completion
     */
    public List<String> tabComplete(Object sender, String[] args) {
        // TODO: Implement tab completion
        return List.of();
    }
    
    // ============================================
    // STUB: Replace with actual Hytale messaging API
    // ============================================
    
    private void sendMessage(Object sender, String message) {
        // TODO: Use actual Hytale API
        // player.sendMessage(message);
        System.out.println(stripColor(message));
    }
    
    private String stripColor(String message) {
        return message.replaceAll("§[0-9a-fk-or]", "");
    }
}
