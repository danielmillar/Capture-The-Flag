package dev.danielmillar.ctf.listeners;

import dev.danielmillar.ctf.service.BossBarService;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerListener implements Listener {

  private final BossBarService bossBarService;

  public PlayerListener(BossBarService bossBarService) {
    this.bossBarService = bossBarService;
  }

  @EventHandler
  public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
    event.joinMessage(null);
    bossBarService.showTo(event.getPlayer());
  }

  @EventHandler
  public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
    event.quitMessage(null);
    bossBarService.hideTo(event.getPlayer());
  }

  @SuppressWarnings("UnstableApiUsage")
  @EventHandler
  public void onPlayerSpawn(AsyncPlayerSpawnLocationEvent event) {
    World world = Bukkit.getWorld("world");
    if (world == null) return;
    event.setSpawnLocation(world.getSpawnLocation());
  }
}
