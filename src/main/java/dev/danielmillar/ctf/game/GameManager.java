package dev.danielmillar.ctf.game;

import dev.danielmillar.ctf.model.TeamData;
import dev.danielmillar.ctf.service.BossBarService;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class GameManager {

  private static final int WIN_SCORE = 3;
  private static final Duration MATCH_DURATION = Duration.ofMinutes(10);
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

  private final JavaPlugin plugin;

  private final Map<Team, TeamData> teams;
  private final Map<UUID, Team> playerTeams;

  private GameState state;
  private long remainingTicks;
  private BukkitTask matchTimerTask;
  private Consumer<String> broadcaster;

  /** Creates a new game manager with default values. */
  public GameManager(JavaPlugin plugin) {
    this.plugin = plugin;
    this.state = GameState.LOBBY;
    this.teams = new EnumMap<>(Team.class);
    this.teams.put(Team.RED, new TeamData());
    this.teams.put(Team.BLUE, new TeamData());
    this.playerTeams = new HashMap<>();
  }

  public static int getWinScore() {
    return WIN_SCORE;
  }

  public static Duration getMatchDuration() {
    return MATCH_DURATION;
  }

  public long getRemainingTicks() {
    return remainingTicks;
  }

  public GameState getState() {
    return state;
  }

  /**
   * Gets an unmodifiable view of the team data map.
   *
   * @return An unmodifiable map of {@link Team} to {@link TeamData}.
   */
  public Map<Team, TeamData> getTeams() {
    return Collections.unmodifiableMap(teams);
  }

  /**
   * Gets the {@link TeamData} for a specific team.
   *
   * @param team The team to look up.
   * @return An {@link Optional} containing the {@link TeamData}, or empty if not found.
   */
  public Optional<TeamData> getTeamData(Team team) {
    return Optional.ofNullable(teams.get(team));
  }

  public Optional<Team> getPlayerTeam(UUID uuid) {
    return Optional.ofNullable(playerTeams.get(uuid));
  }

  /**
   * Assigns a player to a team.
   *
   * @param uuid The player's {@link UUID}.
   * @param team The {@link Team} to assign the player to.
   * @throws NullPointerException if {@code uuid} or {@code team} is {@code null}.
   */
  public void setPlayerTeam(UUID uuid, Team team) {
    Objects.requireNonNull(uuid, "uuid must not be null");
    Objects.requireNonNull(team, "team must not be null");

    Optional<Team> previousTeam = Optional.ofNullable(playerTeams.put(uuid, team));
    previousTeam.flatMap(this::getTeamData).ifPresent(data -> data.removePlayer(uuid));
    getTeamData(team).ifPresent(data -> data.addPlayer(uuid));
  }

  /**
   * Removes a player from their current team.
   *
   * @param uuid The player's {@link UUID}.
   */
  public void removePlayerTeam(UUID uuid) {
    Optional<Team> removedTeam = Optional.ofNullable(playerTeams.remove(uuid));
    removedTeam.flatMap(this::getTeamData).ifPresent(data -> data.removePlayer(uuid));
  }

  /**
   * Spawns the flags for both teams by placing a colored banner at each team's base flag location.
   *
   * @return A {@link Set} of {@link Team}s whose flag failed to spawn (e.g. world unloaded). An
   *     empty set means all flags were placed successfully.
   */
  public Set<Team> spawnFlags() {
    // Must run on the main thread because this mutates blocks.
    Set<Team> failed = EnumSet.noneOf(Team.class);

    teams.forEach(
        (team, data) ->
            data.getBaseFlagLocation()
                .ifPresent(
                    location -> {
                      if (location.getWorld() == null) {
                        failed.add(team);
                        return;
                      }

                      location.getBlock().setType(team.getBannerMaterial());
                    }));

    return failed;
  }

  /**
   * Starts a new game after validating state and setup requirements.
   *
   * @param bossBarService The boss bar service to update during the match.
   * @param broadcaster A callback to broadcast messages to players.
   * @return An {@link Optional} error message if validation fails.
   */
  public Optional<String> startGame(BossBarService bossBarService, Consumer<String> broadcaster) {
    if (state == GameState.RUNNING) {
      return Optional.of("<gray>A game is already in progress.");
    }

    Map<Team, Location> baseLocations = new EnumMap<>(Team.class);
    Optional<String> validationError = validateStart(baseLocations);
    if (validationError.isPresent()) {
      return validationError;
    }

    Location redLoc = baseLocations.get(Team.RED);
    Location blueLoc = baseLocations.get(Team.BLUE);
    if (redLoc != null
        && blueLoc != null
        && redLoc.toBlockLocation().equals(blueLoc.toBlockLocation())) {
      return Optional.of("<gray>Cannot start: both flags are set to the same location.");
    }

    Set<Team> failedFlags = spawnFlags();
    if (!failedFlags.isEmpty()) {
      String failedTeams =
          failedFlags.stream()
              .map(
                  team ->
                      String.format(
                          "<%s>%s</%s>", team.getColor(), team.getDisplayName(), team.getColor()))
              .collect(java.util.stream.Collectors.joining("<gray>, "));
      return Optional.of(
          String.format(
              "<gray>Failed to spawn flags for: %s<gray>. The game has been aborted.",
              failedTeams));
    }

    for (Team team : Team.values()) {
      TeamData data = teams.get(team);
      if (data == null) continue;
      data.reset();

      Location baseLocation = baseLocations.get(team);
      if (baseLocation != null) {
        teleportTeamMembers(data, baseLocation);
      }
    }

    this.broadcaster = broadcaster;
    state = GameState.RUNNING;
    remainingTicks = MATCH_DURATION.toSeconds() * 20L;
    startMatchTimer();
    bossBarService.startMatch();
    broadcaster.accept(
        "<gray><white>Capture the Flag</white> has started. Good luck to all players.");

    return Optional.empty();
  }

  private void startMatchTimer() {
    if (matchTimerTask != null) {
      matchTimerTask.cancel();
    }

    matchTimerTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  if (state != GameState.RUNNING) return;
                  if (remainingTicks <= 0L) return;

                  remainingTicks = Math.max(0L, remainingTicks - 20L);
                  if (remainingTicks == 0L && broadcaster != null) {
                    broadcaster.accept(buildTimeoutMessage());
                    stopGame();
                  }
                },
                20L,
                20L);
  }

  /**
   * Builds a timeout message describing which team has the highest score.
   *
   * @return The MiniMessage-formatted timeout message.
   */
  public String buildTimeoutMessage() {
    Map<Team, TeamData> teamData = getTeams();
    int maxScore = teamData.values().stream().mapToInt(TeamData::getScore).max().orElse(0);

    var leaders =
        teamData.entrySet().stream()
            .filter(entry -> entry.getValue().getScore() == maxScore)
            .map(Map.Entry::getKey)
            .toList();

    if (leaders.isEmpty() || maxScore == 0) {
      return "<gray>The match time has expired. No team scored before time ran out.";
    }

    if (leaders.size() == 1) {
      Team leader = leaders.getFirst();
      return String.format(
          "<gray>The match time has expired. The <%s>%s</%s> <gray>team has the highest score.",
          leader.getColor(), leader.getDisplayName(), leader.getColor());
    }

    String leaderNames =
        leaders.stream()
            .map(
                team ->
                    String.format(
                        "<%s>%s</%s>", team.getColor(), team.getDisplayName(), team.getColor()))
            .collect(java.util.stream.Collectors.joining("<gray>, "));
    return String.format(
        "<gray>The match time has expired. Top score is tied between %s<gray>.", leaderNames);
  }

  /**
   * Stops the current game and cleans up all mutable state and world flags. This is safe to call
   * from both the stop command and plugin shutdown.
   */
  public void stopGame() {
    if (state == GameState.LOBBY) return;

    if (matchTimerTask != null) {
      matchTimerTask.cancel();
      matchTimerTask = null;
    }
    broadcaster = null;
    remainingTicks = 0L;

    teleportAllPlayersToWorldSpawn();
    resetTeamsAndFlags();
    state = GameState.LOBBY;
  }

  /** Resets all team states and clears any placed flags. */
  private void resetTeamsAndFlags() {
    for (Team team : Team.values()) {
      TeamData data = teams.get(team);
      if (data == null) continue;

      data.getCurrentFlagLocation().ifPresent(location -> clearFlagBlock(team, location));

      data.getBaseFlagLocation()
          .ifPresent(
              baseLocation -> {
                boolean sameBlock =
                    data.getCurrentFlagLocation()
                        .map(current -> current.getBlock().equals(baseLocation.getBlock()))
                        .orElse(false);
                if (!sameBlock) {
                  clearFlagBlock(team, baseLocation);
                }
              });

      data.reset();
    }
  }

  /** Resets round state after a capture while preserving team scores. */
  public void resetRound() {
    for (Team team : Team.values()) {
      TeamData data = teams.get(team);
      if (data == null) continue;

      Optional<Location> baseLocationOpt = data.getBaseFlagLocation();
      if (baseLocationOpt.isEmpty()) {
        plugin.getLogger().severe("Missing base flag location for team: " + team.name());
        stopGame();
        return;
      }

      Location baseLocation = baseLocationOpt.get();
      resetFlagsForTeam(team, data, baseLocation);
      data.resetForRound();
      teleportTeamMembers(data, baseLocation);
    }
  }

  /**
   * Drops the flag at the given location if the player is carrying one and broadcasts the event.
   *
   * @param player The player who may be carrying a flag.
   * @param location The location to drop the flag at.
   */
  public void dropFlagIfCarried(Player player, Location location) {
    for (Team flagTeam : Team.values()) {
      Optional<TeamData> dataOpt = getTeamData(flagTeam);
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

  private Optional<String> validateStart(Map<Team, Location> baseLocations) {
    for (Team team : Team.values()) {
      Optional<TeamData> dataOpt = getTeamData(team);
      if (dataOpt.isEmpty()) {
        return Optional.of("<gray>Cannot start: team data is missing.");
      }

      TeamData data = dataOpt.get();
      Optional<Location> baseLocation = data.getBaseFlagLocation();
      if (baseLocation.isEmpty()) {
        return Optional.of(
            String.format(
                "<gray>Cannot start: the <%s>%s</%s> <gray>flag location has not been set. Use <white>/ctf setflag %s</white><gray>.",
                team.getColor(),
                team.getDisplayName(),
                team.getColor(),
                team.name().toLowerCase()));
      }

      long onlineCount =
          data.getMembers().stream().filter(uuid -> Bukkit.getPlayer(uuid) != null).count();
      if (onlineCount < 1) {
        return Optional.of(
            String.format(
                "<gray>Cannot start: the <%s>%s</%s> <gray>team has no online players.",
                team.getColor(), team.getDisplayName(), team.getColor()));
      }

      baseLocations.put(team, baseLocation.get());
    }

    return Optional.empty();
  }

  /**
   * Resets the flag blocks for a team back to their base positions.
   *
   * @param team The team whose flag is being reset.
   * @param data The team's data container.
   * @param baseLocation The base flag location to restore.
   */
  private void resetFlagsForTeam(Team team, TeamData data, Location baseLocation) {
    // Must run on the main thread because this mutates blocks.
    data.getCurrentFlagLocation().ifPresent(location -> clearFlagBlock(team, location));
    clearFlagBlock(team, baseLocation);
    if (baseLocation.getWorld() != null) {
      baseLocation.getBlock().setType(team.getBannerMaterial());
    }
  }

  /**
   * Teleports all online members of the team back to their base location.
   *
   * @param data The team's data container.
   * @param baseLocation The base flag location used as the teleport target.
   */
  private void teleportTeamMembers(TeamData data, Location baseLocation) {
    if (baseLocation.getWorld() == null) return;
    data.getMembers()
        .forEach(
            memberId -> {
              var member = Bukkit.getPlayer(memberId);
              if (member != null) {
                member.teleportAsync(baseLocation);
              }
            });
  }

  /** Teleports all online players to the default world spawn. */
  private void teleportAllPlayersToWorldSpawn() {
    World world = Bukkit.getWorld("world");
    if (world == null) return;

    Location spawnLocation = world.getSpawnLocation();
    Bukkit.getOnlinePlayers().forEach(player -> player.teleportAsync(spawnLocation));
  }

  /**
   * Clears a flag block if it matches the team's banner material.
   *
   * @param team The team associated with the banner material.
   * @param location The location to clear.
   */
  private void clearFlagBlock(Team team, Location location) {
    if (location == null || location.getWorld() == null) return;
    if (location.getBlock().getType() == team.getBannerMaterial()) {
      location.getBlock().setType(Material.AIR);
    }
  }
}
