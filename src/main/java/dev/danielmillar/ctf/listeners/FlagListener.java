package dev.danielmillar.ctf.listeners;

import dev.danielmillar.ctf.game.FlagState;
import dev.danielmillar.ctf.game.GameManager;
import dev.danielmillar.ctf.game.GameState;
import dev.danielmillar.ctf.game.Team;
import dev.danielmillar.ctf.model.TeamData;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

public class FlagListener implements Listener {

  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final Team[] TEAMS = Team.values();

  private final GameManager gameManager;

  public FlagListener(GameManager gameManager) {
    this.gameManager = gameManager;
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    if (gameManager.getState() != GameState.RUNNING) return;
    event.getDrops().clear();
    event.deathMessage(null);

    dropFlagIfCarried(event.getPlayer(), event.getPlayer().getLocation());
  }

  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    if (gameManager.getState() != GameState.RUNNING) return;

    Player player = event.getPlayer();
    Optional<Team> playerTeam = gameManager.getPlayerTeam(player.getUniqueId());
    if (playerTeam.isEmpty()) return;

    gameManager
        .getTeamData(playerTeam.get())
        .flatMap(TeamData::getBaseFlagLocation)
        .filter(location -> location.getWorld() != null)
        .ifPresent(event::setRespawnLocation);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    if (gameManager.getState() != GameState.RUNNING) return;

    dropFlagIfCarried(event.getPlayer(), event.getPlayer().getLocation());
    gameManager.removePlayerTeam(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (gameManager.getState() != GameState.RUNNING) return;

    Block block = event.getClickedBlock();
    if (block == null) return;

    Player player = event.getPlayer();
    Optional<Team> playerTeam = gameManager.getPlayerTeam(player.getUniqueId());

    for (Team flagTeam : TEAMS) {
      Optional<TeamData> dataOpt = gameManager.getTeamData(flagTeam);
      if (dataOpt.isEmpty()) continue;

      TeamData flagData = dataOpt.get();

      if (flagData.getFlagState() == FlagState.CARRIED) continue;

      Optional<Location> currentLoc = flagData.getCurrentFlagLocation();
      if (currentLoc.isEmpty()) continue;
      if (!block.equals(currentLoc.get().getBlock())) continue;

      if (playerTeam.isEmpty()) {
        player.sendMessage(MINI_MESSAGE.deserialize("<gray>You are not on a team."));
        event.setCancelled(true);
        return;
      }

      if (playerTeam.get() == flagTeam) {
        if (flagData.getFlagState() == FlagState.DROPPED) {
          Optional<Location> baseLoc = flagData.getBaseFlagLocation();
          if (baseLoc.isPresent()
              && baseLoc.get().getWorld() != null
              && !block.equals(baseLoc.get().getBlock())) {
            Location baseBlock = baseLoc.get().toBlockLocation();
            block.setType(Material.AIR);
            baseBlock.getBlock().setType(flagTeam.getBannerMaterial());
            flagData.setFlagState(FlagState.AT_BASE);
            flagData.setCurrentFlagLocation(baseBlock);
            event.setCancelled(true);

            player
                .getServer()
                .broadcast(
                    MINI_MESSAGE.deserialize(
                        String.format(
                            "<white>%s</white> <gray>returned the <%s>%s</%s> <gray>flag to base.",
                            player.getName(),
                            flagTeam.getColor(),
                            flagTeam.getDisplayName(),
                            flagTeam.getColor())));
            return;
          }
        }

        Team opponentTeam = playerTeam.get().getOpposite();
        player.sendMessage(
            MINI_MESSAGE.deserialize(
                String.format(
                    "<gray>You cannot pick up your own flag. Pick up the <%s>%s</%s> <gray>flag instead.",
                    opponentTeam.getColor(),
                    opponentTeam.getDisplayName(),
                    opponentTeam.getColor())));
        event.setCancelled(true);
        return;
      }

      flagData.setFlagCarrier(player.getUniqueId());
      flagData.setCurrentFlagLocation(null);
      block.setType(Material.AIR);
      event.setCancelled(true);

      player
          .getServer()
          .broadcast(
              MINI_MESSAGE.deserialize(
                  String.format(
                      "<white>%s</white> <gray>picked up the <%s>%s</%s> <gray>flag.",
                      player.getName(),
                      flagTeam.getColor(),
                      flagTeam.getDisplayName(),
                      flagTeam.getColor())));
      return;
    }
  }

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (gameManager.getState() != GameState.RUNNING) return;
    if (event.getFrom().getBlockX() == event.getTo().getBlockX()
        && event.getFrom().getBlockY() == event.getTo().getBlockY()
        && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
      return;
    }

    Player player = event.getPlayer();
    Optional<Team> playerTeam = gameManager.getPlayerTeam(player.getUniqueId());
    if (playerTeam.isEmpty()) return;

    Team team = playerTeam.get();
    Team enemyTeam = team.getOpposite();

    Optional<TeamData> enemyDataOpt = gameManager.getTeamData(enemyTeam);
    Optional<TeamData> ownDataOpt = gameManager.getTeamData(team);
    if (enemyDataOpt.isEmpty() || ownDataOpt.isEmpty()) return;

    TeamData enemyData = enemyDataOpt.get();
    TeamData ownData = ownDataOpt.get();

    boolean isCarrier =
        enemyData
            .getFlagCarrier()
            .map(carrier -> carrier.equals(player.getUniqueId()))
            .orElse(false);
    if (!isCarrier) return;

    Optional<Location> baseLocation = ownData.getBaseFlagLocation();
    if (baseLocation.isEmpty()) return;
    if (!event.getTo().getBlock().equals(baseLocation.get().getBlock())) return;

    int newScore = ownData.incrementScore();
    player
        .getServer()
        .broadcast(
            MINI_MESSAGE.deserialize(
                String.format(
                    "<white>%s</white> <gray>captured the <%s>%s</%s> <gray>flag! Score: <white>%d</white>",
                    player.getName(),
                    enemyTeam.getColor(),
                    enemyTeam.getDisplayName(),
                    enemyTeam.getColor(),
                    newScore)));

    if (newScore >= GameManager.getWinScore()) {
      player
          .getServer()
          .broadcast(
              MINI_MESSAGE.deserialize(
                  String.format(
                      "<gray>The <%s>%s</%s> <gray>team wins the match.",
                      team.getColor(), team.getDisplayName(), team.getColor())));
      gameManager.stopGame();
      return;
    }

    gameManager.resetRound();
  }

  /**
   * Drops the flag at the given location if the player is carrying one and broadcasts the event.
   *
   * @param player The player who may be carrying a flag.
   * @param location The location to drop the flag at.
   */
  private void dropFlagIfCarried(Player player, Location location) {
    for (Team flagTeam : TEAMS) {
      Optional<TeamData> dataOpt = gameManager.getTeamData(flagTeam);
      if (dataOpt.isEmpty()) continue;

      TeamData flagData = dataOpt.get();

      boolean isCarrier =
          flagData
              .getFlagCarrier()
              .map(carrier -> carrier.equals(player.getUniqueId()))
              .orElse(false);
      if (!isCarrier) continue;

      Location blockLocation = location.toBlockLocation();
      flagData.clearFlagCarrier();
      flagData.setFlagState(FlagState.DROPPED);
      flagData.setCurrentFlagLocation(blockLocation);
      blockLocation.getBlock().setType(flagTeam.getBannerMaterial());

      player
          .getServer()
          .broadcast(
              MINI_MESSAGE.deserialize(
                  String.format(
                      "<white>%s</white> <gray>dropped the <%s>%s</%s> <gray>flag.",
                      player.getName(),
                      flagTeam.getColor(),
                      flagTeam.getDisplayName(),
                      flagTeam.getColor())));
      return;
    }
  }
}
