package com.boxing.command;

import com.boxing.BoxingPlugin;
import com.boxing.model.Arena;
import com.boxing.model.Match;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BoxingCommand implements CommandExecutor, TabCompleter {

    private final BoxingPlugin plugin;

    public BoxingCommand(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("boxing.use")) {
            plugin.getMessageService().send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(player);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "bet" -> handleBet(player, args);
            case "arenas", "list" -> handleArenas(player);
            case "info" -> handleInfo(player, args);
            case "stats", "balance", "bal" -> handleStats(player);
            case "spectate", "spec" -> handleSpectate(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(player, "&cUsage: /boxing join <arena>");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        String result = plugin.getMatchManager().join(player, arena.get());
        switch (result) {
            case "already-in-match" -> plugin.getMessageService().send(player, "already-in-match");
            case "arena-not-ready" -> plugin.getMessageService().send(player, "arena-not-ready", Map.of("arena", arena.get().getName()));
            case "arena-busy" -> plugin.getMessageService().send(player, "arena-busy", Map.of("arena", arena.get().getName()));
            case "economy-missing" -> plugin.getMessageService().send(player, "economy-missing");
            default -> {
            }
        }
    }

    private void handleLeave(Player player) {
        if (!plugin.getMatchManager().leaveQueue(player)) {
            plugin.getMessageService().send(player, "not-in-match");
        }
    }

    private void handleBet(Player player, String[] args) {
        if (!player.hasPermission("boxing.bet")) {
            plugin.getMessageService().send(player, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(player, "&cUsage: /boxing bet <fighter|1|2> <amount> [arena]");
            return;
        }

        Match match;
        if (args.length >= 4) {
            match = plugin.getMatchManager().getMatch(args[3]).orElse(null);
            if (match == null) {
                plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", args[3]));
                return;
            }
        } else {
            var open = findAllOpenBettingMatches();
            if (open.isEmpty()) {
                plugin.getMessageService().sendRaw(player, "&cNo match is open for betting. Specify an arena.");
                return;
            }
            if (open.size() > 1) {
                plugin.getMessageService().sendRaw(player, "&cMultiple matches are open. Use &e/boxing bet <fighter> <amount> <arena>");
                for (Match m : open) {
                    plugin.getMessageService().sendRaw(player, "&8- &e" + m.getArena().getName()
                            + " &7(" + m.getFighter1Name() + " vs " + m.getFighter2Name() + ")");
                }
                return;
            }
            match = open.getFirst();
        }

        UUID target = resolveFighter(match, args[1]);
        if (target == null) {
            plugin.getMessageService().send(player, "invalid-fighter");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(player, "&cInvalid amount.");
            return;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            plugin.getMessageService().sendRaw(player, "&cAmount must be a positive number.");
            return;
        }

        String result = plugin.getMatchManager().placeBet(player, match, target, amount);
        switch (result) {
            case "betting-closed" -> plugin.getMessageService().send(player, "betting-closed");
            case "cannot-bet-self" -> plugin.getMessageService().send(player, "cannot-bet-self");
            case "invalid-fighter" -> plugin.getMessageService().send(player, "invalid-fighter");
            case "economy-missing" -> plugin.getMessageService().send(player, "economy-missing");
            default -> {
            }
        }
    }

    private void handleArenas(Player player) {
        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageService().sendRaw(player, "&eNo arenas configured yet.");
            return;
        }
        plugin.getMessageService().sendRaw(player, "&6Arenas:");
        for (Arena arena : arenas) {
            Match match = plugin.getMatchManager().getMatch(arena.getName()).orElse(null);
            String status;
            if (!arena.isReady()) {
                status = "&csetup incomplete";
            } else if (match == null) {
                status = "&aopen";
            } else {
                status = "&e" + match.getState().name().toLowerCase(Locale.ROOT)
                        + " &7(" + match.fighterCount() + "/2)";
            }
            double fee = plugin.getArenaManager().resolveEntryFee(arena);
            plugin.getMessageService().sendRaw(player,
                    "&8- &f" + arena.getName() + " &7fee:&a" + plugin.getEconomyService().format(fee)
                            + " &7| " + status);
        }
    }

    private void handleInfo(Player player, String[] args) {
        Match match;
        if (args.length >= 2) {
            match = plugin.getMatchManager().getMatch(args[1]).orElse(null);
            if (match == null) {
                plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", args[1]));
                return;
            }
        } else {
            match = plugin.getMatchManager().getMatch(player)
                    .or(() -> findOpenBettingMatch())
                    .orElse(null);
            if (match == null) {
                plugin.getMessageService().sendRaw(player, "&cNo active match. Use /boxing info <arena>");
                return;
            }
        }

        plugin.getMessageService().sendRaw(player, "&6Match: &f" + match.getArena().getName());
        plugin.getMessageService().sendRaw(player, "&7State: &e" + match.getState());
        plugin.getMessageService().sendRaw(player, "&c1: &f" + nullSafe(match.getFighter1Name())
                + " &8| &9" + "2: &f" + nullSafe(match.getFighter2Name()));
        plugin.getMessageService().sendRaw(player, "&7Entry pool: &a" + plugin.getEconomyService().format(match.getEntryPool()));
        plugin.getMessageService().sendRaw(player, "&7Bets: &a" + plugin.getEconomyService().format(match.getTotalBets()));
        plugin.getMessageService().sendRaw(player, "&7Total pot: &a" + plugin.getEconomyService().format(match.getTotalPot()));
        if (match.getFighter1() != null) {
            plugin.getMessageService().sendRaw(player, "&7On " + match.getFighter1Name() + ": &a"
                    + plugin.getEconomyService().format(match.getBetsOn(match.getFighter1())));
        }
        if (match.getFighter2() != null) {
            plugin.getMessageService().sendRaw(player, "&7On " + match.getFighter2Name() + ": &a"
                    + plugin.getEconomyService().format(match.getBetsOn(match.getFighter2())));
        }
    }

    private void handleStats(Player player) {
        plugin.getMessageService().sendRaw(player, "&7Balance: &a"
                + plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player)));
        plugin.getMessageService().sendRaw(player, "&7Economy: &f" + plugin.getEconomyService().describe());
    }

    private void handleSpectate(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(player, "&cUsage: /boxing spectate <arena>");
            return;
        }
        Match own = plugin.getMatchManager().getMatch(player).orElse(null);
        if (own != null && own.getState() == Match.State.FIGHTING && own.isFighter(player.getUniqueId())) {
            plugin.getMessageService().sendRaw(player, "&cYou can't spectate while fighting.");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        if (arena.get().getSpectator() == null && arena.get().getLobby() == null) {
            plugin.getMessageService().sendRaw(player, "&cNo spectator/lobby spawn set for that arena.");
            return;
        }
        player.teleport(arena.get().getSpectator() != null ? arena.get().getSpectator() : arena.get().getLobby());
        Match match = plugin.getMatchManager().getMatch(arena.get().getName()).orElse(null);
        if (match != null) {
            plugin.getScoreboardService().show(player, match);
        }
        plugin.getMessageService().sendRaw(player, "&aTeleported to spectator area for &e" + arena.get().getName());
    }

    private void sendHelp(Player player) {
        plugin.getMessageService().sendRaw(player, "&6Boxing commands:");
        plugin.getMessageService().sendRaw(player, "&e/boxing join <arena> &7- Pay fee and join a fight");
        plugin.getMessageService().sendRaw(player, "&e/boxing leave &7- Leave queue (refund)");
        plugin.getMessageService().sendRaw(player, "&e/boxing bet <fighter> <amount> [arena] &7- Bet on a fighter");
        plugin.getMessageService().sendRaw(player, "&e/boxing arenas &7- List arenas");
        plugin.getMessageService().sendRaw(player, "&e/boxing info [arena] &7- Match / pot info");
        plugin.getMessageService().sendRaw(player, "&e/boxing spectate <arena> &7- Watch a fight");
        plugin.getMessageService().sendRaw(player, "&e/boxing stats &7- Your balance");
    }

    private Optional<Match> findOpenBettingMatch() {
        var open = findAllOpenBettingMatches();
        return open.isEmpty() ? Optional.empty() : Optional.of(open.getFirst());
    }

    private java.util.List<Match> findAllOpenBettingMatches() {
        return plugin.getArenaManager().getArenas().stream()
                .map(a -> plugin.getMatchManager().getMatch(a.getName()).orElse(null))
                .filter(m -> m != null && m.isFull()
                        && (m.getState() == Match.State.WAITING || m.getState() == Match.State.COUNTDOWN))
                .toList();
    }

    private UUID resolveFighter(Match match, String input) {
        String value = input.toLowerCase(Locale.ROOT);
        if (value.equals("1") || value.equals("fighter1") || value.equals("red")) {
            return match.getFighter1();
        }
        if (value.equals("2") || value.equals("fighter2") || value.equals("blue")) {
            return match.getFighter2();
        }
        if (match.getFighter1Name() != null && match.getFighter1Name().equalsIgnoreCase(input)) {
            return match.getFighter1();
        }
        if (match.getFighter2Name() != null && match.getFighter2Name().equalsIgnoreCase(input)) {
            return match.getFighter2();
        }
        Player online = Bukkit.getPlayerExact(input);
        if (online != null && match.isFighter(online.getUniqueId())) {
            return online.getUniqueId();
        }
        return null;
    }

    private String nullSafe(String value) {
        return value == null ? "—" : value;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("join", "leave", "bet", "arenas", "info", "spectate", "stats", "help"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("info") || sub.equals("spectate")) {
                return filter(arenaNames(), args[1]);
            }
            if (sub.equals("bet")) {
                List<String> options = new ArrayList<>(Arrays.asList("1", "2"));
                findOpenBettingMatch().ifPresent(match -> {
                    if (match.getFighter1Name() != null) {
                        options.add(match.getFighter1Name());
                    }
                    if (match.getFighter2Name() != null) {
                        options.add(match.getFighter2Name());
                    }
                });
                return filter(options, args[1]);
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("bet")) {
            return filter(arenaNames(), args[3]);
        }
        return List.of();
    }

    private List<String> arenaNames() {
        return plugin.getArenaManager().getArenas().stream().map(Arena::getName).collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
