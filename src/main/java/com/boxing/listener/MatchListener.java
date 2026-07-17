package com.boxing.listener;

import com.boxing.BoxingPlugin;
import com.boxing.model.Match;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class MatchListener implements Listener {

    private final BoxingPlugin plugin;

    public MatchListener(BoxingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Match match = plugin.getMatchManager().getMatch(victim).orElse(null);
        if (match == null || match.getState() != Match.State.FIGHTING) {
            return;
        }
        if (!match.isFighter(victim.getUniqueId())) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        Player killer = victim.getKiller();
        // Delay slightly so death processing finishes, then end match and revive-style restore
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            victim.spigot().respawn();
            plugin.getMatchManager().handleDeath(victim, killer);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Match match = plugin.getMatchManager().getMatch(victim).orElse(null);
        if (match == null) {
            return;
        }

        if (match.getState() == Match.State.WAITING || match.getState() == Match.State.COUNTDOWN) {
            if (match.isFighter(victim.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        if (match.getState() != Match.State.FIGHTING) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }

        if (attacker == null) {
            return;
        }

        // Only the two fighters may damage each other during a fight
        if (!match.isFighter(victim.getUniqueId()) || !match.isFighter(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMatchManager().handleQuit(event.getPlayer());
        plugin.getScoreboardService().clear(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Match match = plugin.getMatchManager().getMatch(event.getPlayer()).orElse(null);
        if (match == null || match.getState() != Match.State.FIGHTING) {
            return;
        }
        if (!match.isFighter(event.getPlayer().getUniqueId())) {
            return;
        }
        // Allow plugin teleports (plugin cause) and ender pearls if desired; block world changes / commands
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND
                || event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            event.setCancelled(true);
            plugin.getMessageService().sendRaw(event.getPlayer(), "&cYou can't teleport during a fight!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Match match = plugin.getMatchManager().getMatch(event.getPlayer()).orElse(null);
        if (match == null || match.getState() != Match.State.FIGHTING) {
            return;
        }
        if (!match.isFighter(event.getPlayer().getUniqueId())) {
            return;
        }
        String msg = event.getMessage().toLowerCase();
        String base = msg.split(" ")[0];
        if (base.equals("/boxing") || base.equals("/box") || base.equals("/fight")
                || base.equals("/boxingadmin") || base.equals("/boxadmin") || base.equals("/ba")) {
            // Allow boxing leave? During fight leave should forfeit — handled via quit/death only
            if (msg.startsWith("/boxing leave") || msg.startsWith("/box leave") || msg.startsWith("/fight leave")) {
                event.setCancelled(true);
                plugin.getMessageService().sendRaw(event.getPlayer(), "&cYou can't leave mid-fight. Die or disconnect to forfeit.");
            }
            return;
        }
        // Block common escape commands
        if (base.equals("/spawn") || base.equals("/home") || base.equals("/tpa") || base.equals("/tp")
                || base.equals("/warp") || base.equals("/back") || base.equals("/hub") || base.equals("/lobby")) {
            event.setCancelled(true);
            plugin.getMessageService().sendRaw(event.getPlayer(), "&cCommands are restricted during a fight.");
        }
    }
}
