package com.boxing.manager;

import com.boxing.BoxingPlugin;
import com.boxing.model.Arena;
import com.boxing.model.Bet;
import com.boxing.model.Match;
import com.boxing.util.PayoutCalculator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MatchManager {

    private final BoxingPlugin plugin;
    private final Map<String, Match> matchesByArena = new HashMap<>();
    private final Map<UUID, String> playerArena = new HashMap<>();
    private final Map<UUID, Location> pendingRespawns = new HashMap<>();

    public MatchManager(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<Match> getMatch(String arenaName) {
        return Optional.ofNullable(matchesByArena.get(arenaName.toLowerCase()));
    }

    public Optional<Match> getMatch(Player player) {
        String arena = playerArena.get(player.getUniqueId());
        if (arena == null) {
            return Optional.empty();
        }
        return getMatch(arena);
    }

    public boolean isInMatch(Player player) {
        return playerArena.containsKey(player.getUniqueId());
    }

    public Location takePendingRespawn(UUID uuid) {
        return pendingRespawns.remove(uuid);
    }

    public String join(Player player, Arena arena) {
        if (!plugin.getEconomyService().isReady()) {
            return "economy-missing";
        }
        if (isInMatch(player)) {
            return "already-in-match";
        }
        if (!arena.isReady()) {
            return "arena-not-ready";
        }

        Match match = matchesByArena.computeIfAbsent(arena.getName(), name ->
                new Match(arena, plugin.getArenaManager().resolveEntryFee(arena)));

        if (match.getState() != Match.State.WAITING || match.isFull()) {
            return "arena-busy";
        }

        double fee = match.getEntryFee();
        if (!plugin.getEconomyService().ensureFunds(player, fee)) {
            plugin.getMessageService().send(player, "not-enough-money", Map.of(
                    "amount", plugin.getEconomyService().format(fee),
                    "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
            ));
            return "handled";
        }

        if (!plugin.getEconomyService().charge(player, fee)) {
            plugin.getMessageService().send(player, "not-enough-money", Map.of(
                    "amount", plugin.getEconomyService().format(fee),
                    "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
            ));
            return "handled";
        }

        double paid = player.hasPermission("boxing.bypass.fee") ? 0 : fee;
        match.recordEntryPaid(player.getUniqueId(), paid);

        if (match.getFighter1() == null) {
            match.setFighter1(player);
        } else {
            match.setFighter2(player);
        }

        playerArena.put(player.getUniqueId(), arena.getName());
        if (arena.getLobby() != null) {
            player.teleport(arena.getLobby());
        }

        plugin.getScoreboardService().show(player, match);
        startScoreboardTicker(match);

        plugin.getMessageService().send(player, "joined", Map.of(
                "arena", arena.getName(),
                "fee", plugin.getEconomyService().format(fee)
        ));

        if (!match.isFull()) {
            plugin.getMessageService().send(player, "waiting-opponent", Map.of("arena", arena.getName()));
        } else {
            beginCountdown(match);
        }
        return "ok";
    }

    public boolean leaveQueue(Player player) {
        Match match = getMatch(player).orElse(null);
        if (match == null) {
            return false;
        }
        if (match.getState() != Match.State.WAITING && match.getState() != Match.State.COUNTDOWN) {
            return false;
        }

        // Bettors can cancel their stake via /boxing leave
        if (!match.isFighter(player.getUniqueId())) {
            Bet bet = match.getBet(player.getUniqueId());
            if (bet == null) {
                return false;
            }
            offlineDeposit(player.getUniqueId(), bet.getAmount());
            match.getBets().remove(player.getUniqueId());
            untrackIfArena(player.getUniqueId(), match.getArena().getName());
            plugin.getScoreboardService().clear(player);
            plugin.getMessageService().send(player, "bet-cancelled", Map.of(
                    "amount", plugin.getEconomyService().format(bet.getAmount())
            ));
            plugin.getScoreboardService().updateMatch(match);
            return true;
        }

        cancelTasks(match, false);
        refundPlayer(player.getUniqueId(), match);
        match.clearFighter(player.getUniqueId());
        untrackIfArena(player.getUniqueId(), match.getArena().getName());
        plugin.getScoreboardService().clear(player);

        // Fighter left before fight — refund every bet so money cannot vanish
        refundAllBets(match, "&eA fighter left. Your bet of &a{amount}&e was refunded.");

        if (match.getState() == Match.State.COUNTDOWN) {
            match.setState(Match.State.WAITING);
            UUID remaining = match.getFighter1() != null ? match.getFighter1() : match.getFighter2();
            if (remaining != null) {
                Player other = Bukkit.getPlayer(remaining);
                if (other != null) {
                    plugin.getMessageService().sendRaw(other, "&eYour opponent left. Waiting for a new challenger...");
                    plugin.getScoreboardService().update(other, match);
                }
            }
        }

        if (match.fighterCount() == 0) {
            cleanupMatch(match);
        }

        plugin.getMessageService().send(player, "left-queue");
        return true;
    }

    public String placeBet(Player bettor, Match match, UUID targetId, double amount) {
        if (!plugin.getEconomyService().isReady()) {
            return "economy-missing";
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            return "invalid-amount";
        }
        if (match.getState() != Match.State.WAITING && match.getState() != Match.State.COUNTDOWN) {
            return "betting-closed";
        }
        if (match.getState() == Match.State.COUNTDOWN
                && !plugin.getConfig().getBoolean("allow-bets-during-countdown", true)) {
            return "betting-closed";
        }
        if (!match.isFull()) {
            return "betting-closed";
        }
        if (match.isFighter(bettor.getUniqueId())) {
            return "cannot-bet-self";
        }
        if (!match.isFighter(targetId)) {
            return "invalid-fighter";
        }

        double min = plugin.getConfig().getDouble("min-bet", 1.0);
        double max = plugin.getConfig().getDouble("max-bet", 10000.0);
        if (amount < min) {
            plugin.getMessageService().sendRaw(bettor, "&cMinimum bet is &e" + plugin.getEconomyService().format(min));
            return "handled";
        }
        if (max > 0 && amount > max) {
            plugin.getMessageService().sendRaw(bettor, "&cMaximum bet is &e" + plugin.getEconomyService().format(max));
            return "handled";
        }

        Bet existing = match.getBet(bettor.getUniqueId());
        double existingAmount = existing == null ? 0 : existing.getAmount();
        double balance = plugin.getEconomyService().getBalance(bettor);
        if (balance + existingAmount < amount) {
            plugin.getMessageService().send(bettor, "not-enough-money", Map.of(
                    "amount", plugin.getEconomyService().format(amount),
                    "balance", plugin.getEconomyService().format(balance)
            ));
            return "handled";
        }

        if (existing != null) {
            plugin.getEconomyService().deposit(bettor, existing.getAmount());
            match.getBets().remove(bettor.getUniqueId());
        }

        if (!plugin.getEconomyService().withdraw(bettor, amount)) {
            // Restore previous bet if withdraw somehow fails after refund
            if (existing != null) {
                match.putBet(existing);
                plugin.getEconomyService().withdraw(bettor, existing.getAmount());
            }
            return "handled";
        }

        Bet bet = new Bet(
                bettor.getUniqueId(),
                bettor.getName(),
                targetId,
                match.getFighterName(targetId),
                amount
        );
        match.putBet(bet);
        // Never overwrite a fighter's arena mapping
        playerArena.putIfAbsent(bettor.getUniqueId(), match.getArena().getName());
        plugin.getScoreboardService().show(bettor, match);

        String key = existing == null ? "bet-placed" : "bet-updated";
        plugin.getMessageService().send(bettor, key, Map.of(
                "amount", plugin.getEconomyService().format(amount),
                "fighter", match.getFighterName(targetId)
        ));
        plugin.getScoreboardService().updateMatch(match);
        return "ok";
    }

    private void beginCountdown(Match match) {
        match.setState(Match.State.COUNTDOWN);
        int seconds = Math.max(3, plugin.getConfig().getInt("countdown-seconds", 15));
        match.setSecondsLeft(seconds);

        Map<String, String> placeholders = Map.of(
                "player", match.getFighter2Name(),
                "seconds", String.valueOf(seconds)
        );
        notifyFighters(match, "opponent-joined", placeholders);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> !match.isFighter(p.getUniqueId()))
                .forEach(p -> plugin.getMessageService().sendRaw(p,
                        "&eBetting open: &6" + match.getFighter1Name() + " &7vs &6" + match.getFighter2Name()
                                + " &7in &e" + match.getArena().getName()
                                + " &8| &7/boxing bet <fighter> <amount> " + match.getArena().getName()));

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (match.getState() != Match.State.COUNTDOWN) {
                return;
            }
            int left = match.getSecondsLeft();
            if (left <= 0) {
                Bukkit.getScheduler().cancelTask(match.getCountdownTaskId());
                match.setCountdownTaskId(-1);
                startFight(match);
                return;
            }
            if (left <= 5 || left % 5 == 0) {
                for (UUID id : new UUID[]{match.getFighter1(), match.getFighter2()}) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        plugin.getMessageService().actionBar(p, "&eFight starts in &c" + left + "s");
                    }
                }
            }
            match.setSecondsLeft(left - 1);
            plugin.getScoreboardService().updateMatch(match);
        }, 0L, 20L);
        match.setCountdownTaskId(taskId);
    }

    private void startFight(Match match) {
        Player p1 = Bukkit.getPlayer(match.getFighter1());
        Player p2 = Bukkit.getPlayer(match.getFighter2());
        if (p1 == null || p2 == null) {
            abortMatch(match, "A fighter disconnected before the match started.");
            return;
        }

        match.setState(Match.State.FIGHTING);
        prepareFighter(match, p1, match.getArena().getSpawn1());
        prepareFighter(match, p2, match.getArena().getSpawn2());

        plugin.getMessageService().broadcast("match-start", Map.of(
                "fighter1", match.getFighter1Name(),
                "fighter2", match.getFighter2Name()
        ));

        int timeout = plugin.getConfig().getInt("match-timeout-seconds", 180);
        if (timeout > 0) {
            match.setSecondsLeft(timeout);
            int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (match.getState() != Match.State.FIGHTING) {
                    return;
                }
                int left = match.getSecondsLeft();
                if (left <= 0) {
                    Bukkit.getScheduler().cancelTask(match.getTimeoutTaskId());
                    match.setTimeoutTaskId(-1);
                    endDraw(match);
                    return;
                }
                match.setSecondsLeft(left - 1);
                plugin.getScoreboardService().updateMatch(match);
            }, 20L, 20L);
            match.setTimeoutTaskId(taskId);
        }
        plugin.getScoreboardService().updateMatch(match);
    }

    private void prepareFighter(Match match, Player player, Location spawn) {
        match.backupInventory(player);
        if (plugin.getConfig().getBoolean("kit.clear-inventory", true)) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (plugin.getConfig().getBoolean("kit.heal-and-feed", true)) {
            var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                player.setHealth(maxHealth.getValue());
            }
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
        }
        if (plugin.getConfig().getBoolean("kit.give-leather-armor", true)) {
            player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
            player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        }
        if (plugin.getConfig().getBoolean("kit.give-gloves", true)) {
            ItemStack gloves = new ItemStack(Material.WOODEN_SWORD);
            ItemMeta meta = gloves.getItemMeta();
            meta.displayName(Component.text("Boxing Gloves", NamedTextColor.GOLD, TextDecoration.BOLD));
            gloves.setItemMeta(meta);
            player.getInventory().setItemInMainHand(gloves);
        }
        player.setGameMode(GameMode.ADVENTURE);
        if (spawn != null) {
            player.teleport(spawn);
        }
        plugin.getScoreboardService().show(player, match);
    }

    public void handleDeath(Player victim, Player killer) {
        Match match = getMatch(victim).orElse(null);
        if (match == null || match.getState() != Match.State.FIGHTING) {
            return;
        }
        if (!match.isFighter(victim.getUniqueId())) {
            return;
        }

        UUID winnerId = match.getOpponent(victim.getUniqueId());
        if (killer != null && match.isFighter(killer.getUniqueId())
                && !killer.getUniqueId().equals(victim.getUniqueId())) {
            winnerId = killer.getUniqueId();
        }
        if (winnerId == null) {
            endDraw(match);
            return;
        }
        endMatch(match, winnerId, victim.getUniqueId());
    }

    public void handleQuit(Player player) {
        Match match = getMatch(player).orElse(null);
        if (match == null) {
            pendingRespawns.remove(player.getUniqueId());
            return;
        }

        if (match.getState() == Match.State.WAITING || match.getState() == Match.State.COUNTDOWN) {
            leaveQueue(player);
            return;
        }

        if (match.getState() == Match.State.FIGHTING && match.isFighter(player.getUniqueId())) {
            UUID winnerId = match.getOpponent(player.getUniqueId());
            if (winnerId != null) {
                endMatch(match, winnerId, player.getUniqueId());
            } else {
                endDraw(match);
            }
        }
    }

    public void forceStart(Arena arena) {
        Match match = matchesByArena.get(arena.getName());
        if (match == null || !match.isFull()) {
            return;
        }
        cancelTasks(match, false);
        startFight(match);
    }

    public void forceStop(Arena arena) {
        Match match = matchesByArena.get(arena.getName());
        if (match != null) {
            endDraw(match);
        }
    }

    private void endMatch(Match match, UUID winnerId, UUID loserId) {
        if (match.getState() == Match.State.ENDING) {
            return;
        }
        match.setState(Match.State.ENDING);
        cancelTasks(match, true);

        plugin.getMessageService().broadcast("match-win", Map.of(
                "winner", match.getFighterName(winnerId),
                "loser", match.getFighterName(loserId)
        ));

        distributePayouts(match, winnerId);
        restoreAndTeleport(match);
        cleanupMatch(match);
    }

    private void endDraw(Match match) {
        if (match.getState() == Match.State.ENDING) {
            return;
        }
        match.setState(Match.State.ENDING);
        cancelTasks(match, true);
        plugin.getMessageService().broadcast("match-draw", Map.of());

        for (Map.Entry<UUID, Double> entry : new HashMap<>(match.getEntryPaid()).entrySet()) {
            offlineDeposit(entry.getKey(), entry.getValue());
        }
        refundAllBets(match, null);

        restoreAndTeleport(match);
        cleanupMatch(match);
    }

    private void abortMatch(Match match, String reason) {
        match.setState(Match.State.ENDING);
        cancelTasks(match, true);
        plugin.getMessageService().broadcast("match-cancelled", Map.of("reason", reason));
        for (Map.Entry<UUID, Double> entry : new HashMap<>(match.getEntryPaid()).entrySet()) {
            offlineDeposit(entry.getKey(), entry.getValue());
        }
        refundAllBets(match, null);
        restoreAndTeleport(match);
        cleanupMatch(match);
    }

    private void distributePayouts(Match match, UUID winnerId) {
        Map<UUID, Double> betsOnWinner = new LinkedHashMap<>();
        for (Bet bet : match.getBets().values()) {
            if (bet.getTargetId().equals(winnerId)) {
                betsOnWinner.put(bet.getBettorId(), bet.getAmount());
            }
        }

        double winnerShare = plugin.getConfig().getDouble("winner-share", 0.5);
        PayoutCalculator.Result result = PayoutCalculator.calculate(match.getTotalPot(), winnerShare, betsOnWinner);

        offlineDeposit(winnerId, result.winnerPayout());
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) {
            plugin.getMessageService().send(winner, "payout-winner", Map.of(
                    "amount", plugin.getEconomyService().format(result.winnerPayout())
            ));
        }

        for (Map.Entry<UUID, Double> entry : result.bettorPayouts().entrySet()) {
            offlineDeposit(entry.getKey(), entry.getValue());
            Player bettor = Bukkit.getPlayer(entry.getKey());
            if (bettor != null) {
                plugin.getMessageService().send(bettor, "payout-bettor", Map.of(
                        "amount", plugin.getEconomyService().format(entry.getValue()),
                        "winner", match.getFighterName(winnerId)
                ));
            }
        }
    }

    private void restoreAndTeleport(Match match) {
        Location lobby = match.getArena().getLobby();
        for (UUID id : new UUID[]{match.getFighter1(), match.getFighter2()}) {
            if (id == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            restoreInventory(match, player);
            player.setGameMode(GameMode.SURVIVAL);
            plugin.getScoreboardService().clear(player);

            if (lobby != null) {
                if (player.isDead()) {
                    pendingRespawns.put(id, lobby.clone());
                } else {
                    player.teleport(lobby);
                }
            }
        }
        for (UUID bettorId : match.getBets().keySet()) {
            Player bettor = Bukkit.getPlayer(bettorId);
            if (bettor != null && !match.isFighter(bettorId)) {
                plugin.getScoreboardService().clear(bettor);
            }
            untrackIfArena(bettorId, match.getArena().getName());
        }
    }

    private void restoreInventory(Match match, Player player) {
        ItemStack[] contents = match.takeInventoryBackup(player.getUniqueId());
        ItemStack[] armor = match.takeArmorBackup(player.getUniqueId());
        player.getInventory().clear();
        if (contents != null) {
            player.getInventory().setContents(contents);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        if (!player.isDead()) {
            var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                double max = maxHealth.getValue();
                if (player.getHealth() <= 0) {
                    player.setHealth(max);
                } else {
                    player.setHealth(Math.min(player.getHealth(), max));
                }
            }
        }
        player.setFoodLevel(20);
        player.setFireTicks(0);
    }

    private void refundPlayer(UUID uuid, Match match) {
        Double paid = match.takeEntryPaid(uuid);
        if (paid != null && paid > 0) {
            offlineDeposit(uuid, paid);
        }
    }

    private void refundAllBets(Match match, String messageTemplate) {
        for (Bet bet : new HashMap<>(match.getBets()).values()) {
            offlineDeposit(bet.getBettorId(), bet.getAmount());
            Player bettor = Bukkit.getPlayer(bet.getBettorId());
            if (bettor != null && messageTemplate != null) {
                plugin.getMessageService().sendRaw(bettor,
                        messageTemplate.replace("{amount}", plugin.getEconomyService().format(bet.getAmount())));
            }
            if (!match.isFighter(bet.getBettorId())) {
                untrackIfArena(bet.getBettorId(), match.getArena().getName());
                if (bettor != null) {
                    plugin.getScoreboardService().clear(bettor);
                }
            }
        }
        match.getBets().clear();
    }

    private void offlineDeposit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return;
        }
        plugin.getEconomyService().deposit(Bukkit.getOfflinePlayer(uuid), amount);
    }

    private void untrackIfArena(UUID uuid, String arenaName) {
        String current = playerArena.get(uuid);
        if (arenaName.equals(current)) {
            playerArena.remove(uuid);
        }
    }

    private void notifyFighters(Match match, String key, Map<String, String> placeholders) {
        for (UUID id : new UUID[]{match.getFighter1(), match.getFighter2()}) {
            if (id == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                plugin.getMessageService().send(player, key, placeholders);
            }
        }
    }

    private void startScoreboardTicker(Match match) {
        if (match.getScoreboardTaskId() != -1) {
            return;
        }
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!matchesByArena.containsValue(match)) {
                if (match.getScoreboardTaskId() != -1) {
                    Bukkit.getScheduler().cancelTask(match.getScoreboardTaskId());
                    match.setScoreboardTaskId(-1);
                }
                return;
            }
            plugin.getScoreboardService().updateMatch(match);
        }, 20L, 20L);
        match.setScoreboardTaskId(taskId);
    }

    private void cancelTasks(Match match, boolean includeScoreboard) {
        if (match.getCountdownTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(match.getCountdownTaskId());
            match.setCountdownTaskId(-1);
        }
        if (match.getTimeoutTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(match.getTimeoutTaskId());
            match.setTimeoutTaskId(-1);
        }
        if (includeScoreboard && match.getScoreboardTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(match.getScoreboardTaskId());
            match.setScoreboardTaskId(-1);
        }
    }

    private void cleanupMatch(Match match) {
        if (match.getFighter1() != null) {
            untrackIfArena(match.getFighter1(), match.getArena().getName());
        }
        if (match.getFighter2() != null) {
            untrackIfArena(match.getFighter2(), match.getArena().getName());
        }
        for (UUID bettor : new HashMap<>(match.getBets()).keySet()) {
            untrackIfArena(bettor, match.getArena().getName());
        }
        cancelTasks(match, true);
        matchesByArena.remove(match.getArena().getName());
    }

    public void shutdown() {
        for (Match match : new HashMap<>(matchesByArena).values()) {
            endDraw(match);
        }
        matchesByArena.clear();
        playerArena.clear();
        pendingRespawns.clear();
    }
}
