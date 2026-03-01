package dev.danielmillar.ctf.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class WorldListener implements Listener {

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.hasPermission("ctf.bypass") && player.getGameMode() == GameMode.CREATIVE) return;

    event.setCancelled(true);
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (player.hasPermission("ctf.bypass") && player.getGameMode() == GameMode.CREATIVE) return;

    event.setCancelled(true);
  }
}
