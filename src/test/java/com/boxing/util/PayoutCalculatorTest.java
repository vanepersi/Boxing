package com.boxing.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutCalculatorTest {

    @Test
    void winnerTakesHalfAndBettorsSplitRest() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // pot 200, winner share 0.5 => winner 100, bettors 100 split 75/25
        var result = PayoutCalculator.calculate(200, 0.5, Map.of(a, 75.0, b, 25.0));
        assertEquals(100.0, result.winnerPayout(), 0.0001);
        assertEquals(75.0, result.bettorPayouts().get(a), 0.0001);
        assertEquals(25.0, result.bettorPayouts().get(b), 0.0001);
    }

    @Test
    void noWinningBetsGivesFullPotToWinner() {
        var result = PayoutCalculator.calculate(150, 0.5, Map.of());
        assertEquals(150.0, result.winnerPayout(), 0.0001);
        assertTrue(result.bettorPayouts().isEmpty());
    }

    @Test
    void clampsWinnerShare() {
        UUID a = UUID.randomUUID();
        var over = PayoutCalculator.calculate(100, 2.0, Map.of(a, 10.0));
        assertEquals(100.0, over.winnerPayout(), 0.0001);
        assertEquals(0.0, over.bettorPayouts().getOrDefault(a, 0.0), 0.0001);

        var under = PayoutCalculator.calculate(100, -1.0, Map.of(a, 10.0));
        assertEquals(0.0, under.winnerPayout(), 0.0001);
        assertEquals(100.0, under.bettorPayouts().get(a), 0.0001);
    }
}
