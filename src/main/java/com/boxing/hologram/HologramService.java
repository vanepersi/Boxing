package com.boxing.hologram;

import com.boxing.BoxingPlugin;
import com.boxing.model.Arena;
import com.boxing.model.Match;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-world match HUD using TextDisplay entities (not armor stands).
 */
public final class HologramService {

    private final BoxingPlugin plugin;
    private final Map<String, UUID> displaysByArena = new HashMap<>();

    public HologramService(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Match match) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }
        Location location = resolveLocation(match.getArena());
        if (location == null || location.getWorld() == null) {
            return;
        }

        remove(match.getArena().getName());

        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            entity.setLineWidth(200);
            entity.setViewRange((float) plugin.getConfig().getDouble("hologram.view-range", 48.0));
            entity.text(buildText(match));
        });
        displaysByArena.put(match.getArena().getName(), display.getUniqueId());
    }

    public void update(Match match) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }
        UUID id = displaysByArena.get(match.getArena().getName());
        if (id == null) {
            show(match);
            return;
        }
        if (!(Bukkit.getEntity(id) instanceof TextDisplay display) || display.isDead()) {
            show(match);
            return;
        }
        display.text(buildText(match));
    }

    public void remove(String arenaName) {
        String key = arenaName.toLowerCase();
        UUID id = displaysByArena.remove(key);
        if (id == null) {
            return;
        }
        var entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    public void clearAll() {
        for (String arena : Map.copyOf(displaysByArena).keySet()) {
            remove(arena);
        }
        displaysByArena.clear();
    }

    private Location resolveLocation(Arena arena) {
        if (arena.getHologram() != null) {
            return arena.getHologram();
        }
        Location a = arena.getSpawn1();
        Location b = arena.getSpawn2();
        if (a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())) {
            double yOffset = plugin.getConfig().getDouble("hologram.y-offset", 2.5);
            return new Location(
                    a.getWorld(),
                    (a.getX() + b.getX()) / 2.0,
                    (a.getY() + b.getY()) / 2.0 + yOffset,
                    (a.getZ() + b.getZ()) / 2.0
            );
        }
        if (arena.getSpectator() != null) {
            return arena.getSpectator().clone().add(0, plugin.getConfig().getDouble("hologram.y-offset", 2.5), 0);
        }
        if (arena.getLobby() != null) {
            return arena.getLobby().clone().add(0, plugin.getConfig().getDouble("hologram.y-offset", 2.5), 0);
        }
        return null;
    }

    private Component buildText(Match match) {
        Player f1 = match.getFighter1() == null ? null : Bukkit.getPlayer(match.getFighter1());
        Player f2 = match.getFighter2() == null ? null : Bukkit.getPlayer(match.getFighter2());
        String f1Name = match.getFighter1Name() == null ? "—" : match.getFighter1Name();
        String f2Name = match.getFighter2Name() == null ? "—" : match.getFighter2Name();
        String f1Hp = f1 == null ? "—" : String.format("%.0f", f1.getHealth());
        String f2Hp = f2 == null ? "—" : String.format("%.0f", f2.getHealth());
        String state = switch (match.getState()) {
            case WAITING -> "Waiting";
            case COUNTDOWN -> "Countdown";
            case FIGHTING -> "Fighting";
            case ENDING -> "Ending";
        };

        Component title = Component.text("BOXING", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component body = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "\n&7" + match.getArena().getName() + " &8• &e" + state
                        + "\n&c" + f1Name + " &7(" + f1Hp + "❤)"
                        + "\n&8vs"
                        + "\n&9" + f2Name + " &7(" + f2Hp + "❤)"
                        + "\n&7Pot: &a" + plugin.getEconomyService().format(match.getTotalPot())
                        + " &8| &7Bets: &a" + plugin.getEconomyService().format(match.getTotalBets())
                        + ((match.getState() == Match.State.COUNTDOWN || match.getState() == Match.State.FIGHTING)
                        ? "\n&7Time: &e" + match.getSecondsLeft() + "s"
                        : "")
        );
        return title.append(body);
    }
}
