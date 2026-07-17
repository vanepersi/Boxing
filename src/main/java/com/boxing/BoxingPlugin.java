package com.boxing;

import com.boxing.command.BoxingAdminCommand;
import com.boxing.command.BoxingCommand;
import com.boxing.economy.EconomyService;
import com.boxing.listener.EconomyListener;
import com.boxing.listener.MatchListener;
import com.boxing.manager.ArenaManager;
import com.boxing.manager.MatchManager;
import com.boxing.manager.MessageService;
import com.boxing.scoreboard.ScoreboardService;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoxingPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private MatchManager matchManager;
    private EconomyService economyService;
    private MessageService messageService;
    private ScoreboardService scoreboardService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageService = new MessageService(this);
        this.economyService = new EconomyService(this);
        this.arenaManager = new ArenaManager(this);
        this.scoreboardService = new ScoreboardService(this);
        this.matchManager = new MatchManager(this);

        arenaManager.load();
        economyService.hook();

        BoxingCommand boxingCommand = new BoxingCommand(this);
        BoxingAdminCommand adminCommand = new BoxingAdminCommand(this);
        getCommand("boxing").setExecutor(boxingCommand);
        getCommand("boxing").setTabCompleter(boxingCommand);
        getCommand("boxingadmin").setExecutor(adminCommand);
        getCommand("boxingadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getPluginManager().registerEvents(new EconomyListener(this), this);
        }

        getLogger().info("Boxing enabled. Economy: " + economyService.describe());
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (arenaManager != null) {
            arenaManager.save();
        }
        if (scoreboardService != null) {
            scoreboardService.clearAll();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        messageService.reload();
        arenaManager.load();
        economyService.hook();
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public ScoreboardService getScoreboardService() {
        return scoreboardService;
    }
}
