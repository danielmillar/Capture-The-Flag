package dev.danielmillar.ctf.service;

import dev.danielmillar.ctf.game.GameManager;
import dev.danielmillar.ctf.game.GameState;
import dev.danielmillar.ctf.game.Team;
import dev.danielmillar.ctf.model.TeamData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BossBarService {

  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

  private final JavaPlugin plugin;
  private final GameManager gameManager;

  private BossBar bossBar;
  private BukkitTask bossBarTask;

  public BossBarService(JavaPlugin plugin, GameManager gameManager) {
    this.plugin = plugin;
    this.gameManager = gameManager;
  }

  /** Starts the boss bar update task if it is not already running. */
  public void start() {
    if (bossBarTask != null) return;

    bossBar =
        BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 0L, 20L);
  }

  /** Starts the boss bar and resets the match timer. */
  public void startMatch() {
    start();
  }

  /** Stops the boss bar update task and clears all players from the bar. */
  public void stop() {
    if (bossBarTask != null) {
      bossBarTask.cancel();
      bossBarTask = null;
    }
  }

  /**
   * Shows the boss bar to the player.
   *
   * @param player The player to show the boss bar to
   */
  public void showTo(Player player) {
    if (bossBar != null) player.showBossBar(bossBar);
  }

  /**
   * Hides the boss bar from the player.
   *
   * @param player The player to hide the boss bar from.
   */
  public void hideTo(Player player) {
    if (bossBar != null) player.hideBossBar(bossBar);
  }

  private void update() {
    if (bossBar == null) return;

    if (gameManager.getState() != GameState.RUNNING) {
      bossBar.name(MINI_MESSAGE.deserialize("<gray>You're currently in the lobby"));
      bossBar.color(BossBar.Color.WHITE);
      bossBar.progress(1.0f);
      return;
    }

    TeamData redData = gameManager.getTeams().get(Team.RED);
    TeamData blueData = gameManager.getTeams().get(Team.BLUE);
    int redScore = redData == null ? 0 : redData.getScore();
    int blueScore = blueData == null ? 0 : blueData.getScore();

    long remainingTicks = gameManager.getRemainingTicks();
    long remainingSeconds = remainingTicks / 20L;
    long minutes = remainingSeconds / 60L;
    long seconds = remainingSeconds % 60L;
    String timeRemaining = String.format("%02d:%02d", minutes, seconds);

    long totalTicks = GameManager.getMatchDuration().toSeconds() * 20L;
    float progress =
        totalTicks <= 0
            ? 0.0f
            : (float) Math.max(0.0, Math.min(1.0, (double) remainingTicks / totalTicks));

    String title =
        String.format(
            "<%s>%s</%s> <white>%d</white> <gray>-</gray> <%s>%s</%s> <white>%d</white> <gray>|</gray> <white>%s</white>",
            Team.RED.getColor(),
            Team.RED.getDisplayName(),
            Team.RED.getColor(),
            redScore,
            Team.BLUE.getColor(),
            Team.BLUE.getDisplayName(),
            Team.BLUE.getColor(),
            blueScore,
            timeRemaining);
    bossBar.name(MINI_MESSAGE.deserialize(title));
    bossBar.progress(progress);
    bossBar.color(getLeadingColor(redScore, blueScore));
  }

  private BossBar.Color getLeadingColor(int redScore, int blueScore) {
    if (redScore > blueScore) return BossBar.Color.RED;
    if (blueScore > redScore) return BossBar.Color.BLUE;
    return BossBar.Color.WHITE;
  }
}
