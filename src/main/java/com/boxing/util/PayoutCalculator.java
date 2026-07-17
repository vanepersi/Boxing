package com.boxing.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure payout math so it can be unit-tested without Bukkit.
 */
public final class PayoutCalculator {

    public record Result(double winnerPayout, Map<UUID, Double> bettorPayouts) {
    }

    private PayoutCalculator() {
    }

    /**
     * @param pot              total pot (entry fees + all bets)
     * @param winnerShare      fraction paid to the winning fighter (0..1)
     * @param betsOnWinner     map of bettorId -> stake for bets that picked the winner
     */
    public static Result calculate(double pot, double winnerShare, Map<UUID, Double> betsOnWinner) {
        if (!Double.isFinite(pot) || pot < 0) {
            pot = 0;
        }
        if (!Double.isFinite(winnerShare)) {
            winnerShare = 0.5;
        }
        winnerShare = Math.max(0, Math.min(1, winnerShare));

        double winningBetsTotal = 0;
        if (betsOnWinner != null) {
            for (double amount : betsOnWinner.values()) {
                if (Double.isFinite(amount) && amount > 0) {
                    winningBetsTotal += amount;
                }
            }
        }

        double winnerPayout;
        double bettorPool;
        if (winningBetsTotal <= 0) {
            winnerPayout = pot;
            bettorPool = 0;
        } else {
            winnerPayout = pot * winnerShare;
            bettorPool = pot - winnerPayout;
        }

        Map<UUID, Double> bettorPayouts = new LinkedHashMap<>();
        if (bettorPool > 0 && betsOnWinner != null) {
            for (Map.Entry<UUID, Double> entry : betsOnWinner.entrySet()) {
                double stake = entry.getValue();
                if (!Double.isFinite(stake) || stake <= 0) {
                    continue;
                }
                bettorPayouts.put(entry.getKey(), bettorPool * (stake / winningBetsTotal));
            }
        }
        return new Result(winnerPayout, bettorPayouts);
    }
}
