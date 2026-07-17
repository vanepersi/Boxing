package com.boxing.manager;

import com.boxing.BoxingPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageService {

    private final BoxingPlugin plugin;
    private String prefix;

    public MessageService(BoxingPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prefix = color(plugin.getConfig().getString("messages.prefix", "&8[&6Boxing&8] &r"));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, key);
        String message = apply(raw, placeholders);
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + message));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + message));
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, key);
        String message = apply(raw, placeholders);
        plugin.getServer().broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + message));
    }

    public String apply(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public String color(String input) {
        return input == null ? "" : input;
    }

    public void actionBar(Player player, String message) {
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
