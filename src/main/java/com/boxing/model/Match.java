package com.boxing.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Match {

    public enum State {
        WAITING,
        COUNTDOWN,
        FIGHTING,
        ENDING
    }

    private final Arena arena;
    private final double entryFee;
    private State state = State.WAITING;

    private UUID fighter1;
    private UUID fighter2;
    private String fighter1Name;
    private String fighter2Name;

    private final Map<UUID, Bet> bets = new HashMap<>();
    private final Map<UUID, ItemStack[]> inventoryBackup = new HashMap<>();
    private final Map<UUID, ItemStack[]> armorBackup = new HashMap<>();
    private final Map<UUID, Double> entryPaid = new HashMap<>();

    private int countdownTaskId = -1;
    private int timeoutTaskId = -1;
    private int scoreboardTaskId = -1;
    private int secondsLeft;

    public Match(Arena arena, double entryFee) {
        this.arena = arena;
        this.entryFee = entryFee;
    }

    public Arena getArena() {
        return arena;
    }

    public double getEntryFee() {
        return entryFee;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public UUID getFighter1() {
        return fighter1;
    }

    public UUID getFighter2() {
        return fighter2;
    }

    public String getFighter1Name() {
        return fighter1Name;
    }

    public String getFighter2Name() {
        return fighter2Name;
    }

    public void setFighter1(Player player) {
        this.fighter1 = player.getUniqueId();
        this.fighter1Name = player.getName();
    }

    public void setFighter2(Player player) {
        this.fighter2 = player.getUniqueId();
        this.fighter2Name = player.getName();
    }

    public void clearFighter(UUID uuid) {
        if (uuid.equals(fighter1)) {
            fighter1 = null;
            fighter1Name = null;
        } else if (uuid.equals(fighter2)) {
            fighter2 = null;
            fighter2Name = null;
        }
    }

    public boolean isFighter(UUID uuid) {
        return uuid.equals(fighter1) || uuid.equals(fighter2);
    }

    public boolean isFull() {
        return fighter1 != null && fighter2 != null;
    }

    public int fighterCount() {
        int count = 0;
        if (fighter1 != null) {
            count++;
        }
        if (fighter2 != null) {
            count++;
        }
        return count;
    }

    public UUID getOpponent(UUID uuid) {
        if (uuid.equals(fighter1)) {
            return fighter2;
        }
        if (uuid.equals(fighter2)) {
            return fighter1;
        }
        return null;
    }

    public String getFighterName(UUID uuid) {
        if (uuid.equals(fighter1)) {
            return fighter1Name;
        }
        if (uuid.equals(fighter2)) {
            return fighter2Name;
        }
        return "Unknown";
    }

    public int getFighterSlot(UUID uuid) {
        if (uuid.equals(fighter1)) {
            return 1;
        }
        if (uuid.equals(fighter2)) {
            return 2;
        }
        return 0;
    }

    public UUID getFighterBySlot(int slot) {
        return slot == 1 ? fighter1 : slot == 2 ? fighter2 : null;
    }

    public Map<UUID, Bet> getBets() {
        return bets;
    }

    public void putBet(Bet bet) {
        bets.put(bet.getBettorId(), bet);
    }

    public Bet getBet(UUID bettorId) {
        return bets.get(bettorId);
    }

    public double getTotalBets() {
        return bets.values().stream().mapToDouble(Bet::getAmount).sum();
    }

    public double getBetsOn(UUID fighterId) {
        return bets.values().stream()
                .filter(bet -> bet.getTargetId().equals(fighterId))
                .mapToDouble(Bet::getAmount)
                .sum();
    }

    public double getEntryPool() {
        return entryPaid.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getTotalPot() {
        return getEntryPool() + getTotalBets();
    }

    public void recordEntryPaid(UUID uuid, double amount) {
        entryPaid.put(uuid, amount);
    }

    public Double takeEntryPaid(UUID uuid) {
        return entryPaid.remove(uuid);
    }

    public Map<UUID, Double> getEntryPaid() {
        return entryPaid;
    }

    public void backupInventory(Player player) {
        inventoryBackup.put(player.getUniqueId(), player.getInventory().getContents().clone());
        armorBackup.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
    }

    public ItemStack[] takeInventoryBackup(UUID uuid) {
        return inventoryBackup.remove(uuid);
    }

    public ItemStack[] takeArmorBackup(UUID uuid) {
        return armorBackup.remove(uuid);
    }

    public int getCountdownTaskId() {
        return countdownTaskId;
    }

    public void setCountdownTaskId(int countdownTaskId) {
        this.countdownTaskId = countdownTaskId;
    }

    public int getTimeoutTaskId() {
        return timeoutTaskId;
    }

    public void setTimeoutTaskId(int timeoutTaskId) {
        this.timeoutTaskId = timeoutTaskId;
    }

    public int getScoreboardTaskId() {
        return scoreboardTaskId;
    }

    public void setScoreboardTaskId(int scoreboardTaskId) {
        this.scoreboardTaskId = scoreboardTaskId;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public void setSecondsLeft(int secondsLeft) {
        this.secondsLeft = secondsLeft;
    }
}
