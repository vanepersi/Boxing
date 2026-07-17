package com.boxing.scoreboard;

import com.boxing.BoxingPlugin;
import com.boxing.model.Match;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardService {

    private final BoxingPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardService(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, Match match) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        String title = plugin.getConfig().getString("scoreboard-title", "&6&lBOXING");
        Objective objective = scoreboard.registerNewObjective(
                "boxing",
                Criteria.DUMMY,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        boards.put(player.getUniqueId(), scoreboard);
        player.setScoreboard(scoreboard);
        update(player, match);
    }

    public void update(Player player, Match match) {
        Scoreboard scoreboard = boards.get(player.getUniqueId());
        if (scoreboard == null) {
            show(player, match);
            scoreboard = boards.get(player.getUniqueId());
        }
        Objective objective = scoreboard.getObjective("boxing");
        if (objective == null) {
            return;
        }

        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

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

        int line = 15;
        set(objective, "&7Arena: &f" + match.getArena().getName(), line--);
        set(objective, "&7State: &e" + state, line--);
        set(objective, " ", line--);
        set(objective, "&c" + f1Name, line--);
        set(objective, "&7HP: &c" + f1Hp, line--);
        set(objective, "&8vs", line--);
        set(objective, "&9" + f2Name, line--);
        set(objective, "&7HP: &9" + f2Hp, line--);
        set(objective, "  ", line--);
        set(objective, "&7Pot: &a" + plugin.getEconomyService().format(match.getTotalPot()), line--);
        set(objective, "&7Bets: &a" + plugin.getEconomyService().format(match.getTotalBets()), line--);
        if (match.getState() == Match.State.COUNTDOWN || match.getState() == Match.State.FIGHTING) {
            set(objective, "&7Time: &e" + match.getSecondsLeft() + "s", line);
        }
    }

    public void updateMatch(Match match) {
        for (UUID uuid : boards.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (match.isFighter(uuid) || match.getBets().containsKey(uuid)) {
                    update(player, match);
                }
            }
        }
        // Also update nearby spectators with the board already shown
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(player.getUniqueId())) {
                update(player, match);
            }
        }
        plugin.getHologramService().update(match);
    }

    public void clear(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearAll() {
        for (UUID uuid : boards.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    private void set(Objective objective, String text, int score) {
        // Make entries unique by appending invisible color codes based on score
        String entry = color(text) + uniqueSuffix(score);
        objective.getScore(entry).setScore(score);
    }

    private String color(String input) {
        return LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(input)
        );
    }

    private String uniqueSuffix(int score) {
        return "§" + Integer.toHexString(score % 16);
    }
}
