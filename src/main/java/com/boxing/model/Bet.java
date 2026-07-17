package com.boxing.model;

import java.util.UUID;

public final class Bet {

    private final UUID bettorId;
    private final String bettorName;
    private UUID targetId;
    private String targetName;
    private double amount;

    public Bet(UUID bettorId, String bettorName, UUID targetId, String targetName, double amount) {
        this.bettorId = bettorId;
        this.bettorName = bettorName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.amount = amount;
    }

    public UUID getBettorId() {
        return bettorId;
    }

    public String getBettorName() {
        return bettorName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
