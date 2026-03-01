package dev.danielmillar.ctf;

import dev.danielmillar.ctf.commands.CommandRegistrar;
import dev.danielmillar.ctf.game.GameManager;
import dev.danielmillar.ctf.listeners.FlagListener;
import dev.danielmillar.ctf.listeners.WorldListener;
import dev.danielmillar.ctf.service.BossBarService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaptureTheFlag extends JavaPlugin {

  private GameManager gameManager;
  private BossBarService bossBarService;

  @Override
  public void onEnable() {
    gameManager = new GameManager(this);
    bossBarService = new BossBarService(this, gameManager);
    bossBarService.start();

    getServer().getPluginManager().registerEvents(new WorldListener(), this);
    getServer().getPluginManager().registerEvents(new FlagListener(gameManager), this);

    getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> {
              CommandRegistrar registrar = new CommandRegistrar(gameManager, bossBarService);
              event.registrar().register(registrar.root().build());
            });
  }

  @Override
  public void onDisable() {
    if (bossBarService != null) {
      bossBarService.stop();
    }

    if (gameManager != null) {
      gameManager.stopGame();
    }
  }
}
