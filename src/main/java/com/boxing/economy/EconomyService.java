package com.boxing.economy;

import com.boxing.BoxingPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Prefers Vault when available; otherwise uses a simple built-in balance file.
 */
public final class EconomyService {

    private final BoxingPlugin plugin;
    private Economy vault;
    private File balancesFile;
    private FileConfiguration balances;
    private boolean usingVault;

    public EconomyService(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        vault = null;
        usingVault = false;

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> registration =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) {
                vault = registration.getProvider();
                usingVault = vault != null;
            }
        }

        if (!usingVault) {
            balancesFile = new File(plugin.getDataFolder(), "balances.yml");
            if (!balancesFile.exists()) {
                plugin.getDataFolder().mkdirs();
                try {
                    balancesFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create balances.yml", e);
                }
            }
            balances = YamlConfiguration.loadConfiguration(balancesFile);
            plugin.getLogger().warning("Vault economy not found. Using built-in balances.yml (starting balance 1000).");
        }
    }

    public String describe() {
        return usingVault ? "Vault (" + vault.getName() + ")" : "built-in";
    }

    public boolean isReady() {
        return usingVault || balances != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (usingVault) {
            return vault.getBalance(player);
        }
        return balances.getDouble(player.getUniqueId().toString(), 1000.0);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!has(player, amount)) {
            return false;
        }
        if (usingVault) {
            return vault.withdrawPlayer(player, amount).transactionSuccess();
        }
        setBalance(player.getUniqueId(), getBalance(player) - amount);
        return true;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (usingVault) {
            return vault.depositPlayer(player, amount).transactionSuccess();
        }
        setBalance(player.getUniqueId(), getBalance(player) + amount);
        return true;
    }

    public String format(double amount) {
        if (usingVault) {
            return vault.format(amount);
        }
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format("%.2f", amount);
    }

    public boolean ensureFunds(Player player, double amount) {
        if (player.hasPermission("boxing.bypass.fee") && amount > 0) {
            return true;
        }
        return has(player, amount);
    }

    public boolean charge(Player player, double amount) {
        if (amount <= 0 || player.hasPermission("boxing.bypass.fee")) {
            return true;
        }
        return withdraw(player, amount);
    }

    private void setBalance(UUID uuid, double amount) {
        balances.set(uuid.toString(), Math.max(0, amount));
        try {
            balances.save(balancesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save balances.yml", e);
        }
    }
}
