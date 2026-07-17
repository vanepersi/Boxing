package com.boxing.manager;

import com.boxing.BoxingPlugin;
import com.boxing.model.Arena;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class ArenaManager {

    private final BoxingPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private File file;
    private FileConfiguration config;

    public ArenaManager(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create arenas.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                arenas.put(key.toLowerCase(), Arena.deserialize(key, section.getConfigurationSection(key)));
            }
        }
    }

    public void save() {
        if (config == null || file == null) {
            return;
        }
        config.set("arenas", null);
        for (Arena arena : arenas.values()) {
            config.createSection("arenas." + arena.getName(), arena.serialize());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
        }
    }

    public Optional<Arena> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenas.get(name.toLowerCase()));
    }

    public Collection<Arena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public boolean exists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arenas.put(arena.getName(), arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        Arena removed = arenas.remove(name.toLowerCase());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public double resolveEntryFee(Arena arena) {
        if (arena.getEntryFeeOverride() != null) {
            return arena.getEntryFeeOverride();
        }
        return plugin.getConfig().getDouble("entry-fee", 100.0);
    }
}
