package com.boxing.listener;

import com.boxing.BoxingPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;

/**
 * Re-hooks Vault when an economy provider registers after Boxing enables.
 */
public final class EconomyListener implements Listener {

    private final BoxingPlugin plugin;

    public EconomyListener(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (Economy.class.isAssignableFrom(event.getProvider().getService())) {
            plugin.getEconomyService().hook();
            plugin.getLogger().info("Economy service registered. Now using: " + plugin.getEconomyService().describe());
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (Economy.class.isAssignableFrom(event.getProvider().getService())) {
            plugin.getEconomyService().hook();
            plugin.getLogger().info("Economy service unregistered. Now using: " + plugin.getEconomyService().describe());
        }
    }
}
