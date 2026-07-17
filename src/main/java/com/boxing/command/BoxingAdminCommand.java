package com.boxing.command;

import com.boxing.BoxingPlugin;
import com.boxing.model.Arena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class BoxingAdminCommand implements CommandExecutor, TabCompleter {

    private final BoxingPlugin plugin;

    public BoxingAdminCommand(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boxing.admin")) {
            plugin.getMessageService().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "create" -> handleCreate(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "setspawn1", "spawn1" -> handleSetSpawn(sender, args, "spawn1");
            case "setspawn2", "spawn2" -> handleSetSpawn(sender, args, "spawn2");
            case "setspectator", "setspectate", "spectator" -> handleSetSpawn(sender, args, "spectator");
            case "setlobby", "lobby" -> handleSetSpawn(sender, args, "lobby");
            case "setfee", "fee" -> handleSetFee(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "forcestart" -> handleForceStart(sender, args);
            case "forcestop", "cancel" -> handleForceStop(sender, args);
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.getMessageService().send(sender, "reloaded");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /boxingadmin create <name>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (plugin.getArenaManager().exists(name)) {
            plugin.getMessageService().sendRaw(sender, "&cArena already exists.");
            return;
        }
        plugin.getArenaManager().create(name);
        plugin.getMessageService().send(sender, "arena-created", Map.of("arena", name));
        plugin.getMessageService().sendRaw(sender, "&7Next: setspawn1, setspawn2, setlobby (optional: setspectator, setfee)");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /boxingadmin delete <name>");
            return;
        }
        String name = args[1];
        if (plugin.getMatchManager().getMatch(name).isPresent()) {
            plugin.getMessageService().sendRaw(sender, "&cStop the active match first: /boxingadmin forcestop " + name);
            return;
        }
        if (!plugin.getArenaManager().delete(name)) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", name));
            return;
        }
        plugin.getMessageService().send(sender, "arena-deleted", Map.of("arena", name.toLowerCase(Locale.ROOT)));
    }

    private void handleSetSpawn(CommandSender sender, String[] args, String spawnType) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /boxingadmin " + spawnType.replace("set", "set") + " <arena>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Arena arena = optional.get();
        switch (spawnType) {
            case "spawn1" -> arena.setSpawn1(player.getLocation());
            case "spawn2" -> arena.setSpawn2(player.getLocation());
            case "spectator" -> arena.setSpectator(player.getLocation());
            case "lobby" -> arena.setLobby(player.getLocation());
            default -> {
                return;
            }
        }
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "spawn-set", Map.of(
                "spawn", spawnType,
                "arena", arena.getName()
        ));
    }

    private void handleSetFee(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /boxingadmin setfee <arena> <amount>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        double fee;
        try {
            fee = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        if (!Double.isFinite(fee) || fee < 0) {
            plugin.getMessageService().sendRaw(sender, "&cFee must be a non-negative number.");
            return;
        }
        Arena arena = optional.get();
        arena.setEntryFeeOverride(fee);
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "fee-set", Map.of(
                "arena", arena.getName(),
                "fee", plugin.getEconomyService().format(fee)
        ));
    }

    private void handleList(CommandSender sender) {
        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&eNo arenas.");
            return;
        }
        for (Arena arena : arenas) {
            plugin.getMessageService().sendRaw(sender, "&8- &f" + arena.getName()
                    + (arena.isReady() ? " &aready" : " &cincomplete")
                    + " &7fee:" + plugin.getEconomyService().format(plugin.getArenaManager().resolveEntryFee(arena)));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /boxingadmin info <arena>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Arena arena = optional.get();
        plugin.getMessageService().sendRaw(sender, "&6Arena &f" + arena.getName());
        plugin.getMessageService().sendRaw(sender, "&7Ready: " + (arena.isReady() ? "&ayes" : "&cno (need spawn1, spawn2, lobby)"));
        plugin.getMessageService().sendRaw(sender, "&7Spawn1: " + loc(arena.getSpawn1()));
        plugin.getMessageService().sendRaw(sender, "&7Spawn2: " + loc(arena.getSpawn2()));
        plugin.getMessageService().sendRaw(sender, "&7Lobby: " + loc(arena.getLobby()));
        plugin.getMessageService().sendRaw(sender, "&7Spectator: " + loc(arena.getSpectator()));
        plugin.getMessageService().sendRaw(sender, "&7Fee: &a" + plugin.getEconomyService().format(plugin.getArenaManager().resolveEntryFee(arena)));
    }

    private void handleForceStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /boxingadmin forcestart <arena>");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        if (plugin.getMatchManager().getMatch(arena.get().getName()).filter(m -> m.isFull()).isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&cNeed two fighters in that arena.");
            return;
        }
        plugin.getMatchManager().forceStart(arena.get());
        plugin.getMessageService().send(sender, "force-started", Map.of("arena", arena.get().getName()));
    }

    private void handleForceStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /boxingadmin forcestop <arena>");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        if (plugin.getMatchManager().getMatch(arena.get().getName()).isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&cNo active match in that arena.");
            return;
        }
        plugin.getMatchManager().forceStop(arena.get());
        plugin.getMessageService().send(sender, "force-stopped", Map.of("arena", arena.get().getName()));
    }

    private String loc(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return "&cunset";
        }
        return "&a" + location.getWorld().getName()
                + " " + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageService().sendRaw(sender, "&6Boxing admin:");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin create <name>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin delete <name>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin setspawn1 <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin setspawn2 <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin setlobby <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin setspectator <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin setfee <arena> <amount>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin list | info <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin forcestart|forcestop <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/boxingadmin reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("boxing.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(Arrays.asList(
                    "create", "delete", "setspawn1", "setspawn2", "setlobby", "setspectator",
                    "setfee", "list", "info", "forcestart", "forcestop", "reload", "help"
            ), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("delete", "setspawn1", "setspawn2", "setlobby", "setspectator", "setspectate",
                    "setfee", "info", "forcestart", "forcestop", "spawn1", "spawn2", "lobby", "spectator", "fee")
                    .contains(sub)) {
                return filter(arenaNames(), args[1]);
            }
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
