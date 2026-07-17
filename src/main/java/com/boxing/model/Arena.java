package com.boxing.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Arena {

    private final String name;
    private Location spawn1;
    private Location spawn2;
    private Location spectator;
    private Location lobby;
    private Location hologram;
    private Double entryFeeOverride;

    public Arena(String name) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public Location getSpawn1() {
        return cloneLocation(spawn1);
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = cloneLocation(spawn1);
    }

    public Location getSpawn2() {
        return cloneLocation(spawn2);
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = cloneLocation(spawn2);
    }

    public Location getSpectator() {
        return cloneLocation(spectator);
    }

    public void setSpectator(Location spectator) {
        this.spectator = cloneLocation(spectator);
    }

    public Location getLobby() {
        return cloneLocation(lobby);
    }

    public void setLobby(Location lobby) {
        this.lobby = cloneLocation(lobby);
    }

    public Location getHologram() {
        return cloneLocation(hologram);
    }

    public void setHologram(Location hologram) {
        this.hologram = cloneLocation(hologram);
    }

    public Double getEntryFeeOverride() {
        return entryFeeOverride;
    }

    public void setEntryFeeOverride(Double entryFeeOverride) {
        this.entryFeeOverride = entryFeeOverride;
    }

    public boolean isReady() {
        return spawn1 != null && spawn2 != null && lobby != null;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("spawn1", serializeLocation(spawn1));
        map.put("spawn2", serializeLocation(spawn2));
        map.put("spectator", serializeLocation(spectator));
        map.put("lobby", serializeLocation(lobby));
        map.put("hologram", serializeLocation(hologram));
        if (entryFeeOverride != null) {
            map.put("entry-fee", entryFeeOverride);
        }
        return map;
    }

    public static Arena deserialize(String name, ConfigurationSection section) {
        Arena arena = new Arena(name);
        if (section == null) {
            return arena;
        }
        arena.spawn1 = deserializeLocation(section.getConfigurationSection("spawn1"));
        arena.spawn2 = deserializeLocation(section.getConfigurationSection("spawn2"));
        arena.spectator = deserializeLocation(section.getConfigurationSection("spectator"));
        arena.lobby = deserializeLocation(section.getConfigurationSection("lobby"));
        arena.hologram = deserializeLocation(section.getConfigurationSection("hologram"));
        if (section.contains("entry-fee")) {
            arena.entryFeeOverride = section.getDouble("entry-fee");
        }
        return arena;
    }

    private static Map<String, Object> serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private static Location deserializeLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Arena arena)) {
            return false;
        }
        return Objects.equals(name, arena.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
