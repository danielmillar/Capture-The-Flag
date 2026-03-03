package dev.danielmillar.ctf.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.danielmillar.ctf.game.GameManager;
import dev.danielmillar.ctf.game.GameState;
import dev.danielmillar.ctf.game.Team;
import dev.danielmillar.ctf.model.TeamData;
import dev.danielmillar.ctf.service.BossBarService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CommandRegistrar {

  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final Team[] TEAMS = Team.values();

  private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS =
      (context, builder) -> {
        for (Team t : TEAMS) {
          builder.suggest(t.name().toLowerCase());
        }
        return builder.buildFuture();
      };

  private final GameManager gameManager;
  private final BossBarService bossBarService;

  public CommandRegistrar(GameManager gameManager, BossBarService bossBarService) {
    this.gameManager = gameManager;
    this.bossBarService = bossBarService;
  }

  /**
   * Creates the root command and registers all sub-commands.
   *
   * @return The root command builder.
   */
  public LiteralArgumentBuilder<CommandSourceStack> root() {
    LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ctf");
    root.then(joinCommand());
    root.then(leaveCommand());
    root.then(setFlagCommand());
    root.then(startCommand());
    root.then(stopCommand());
    root.then(scoreCommand());
    return root;
  }

  /**
   * Creates the join command.
   *
   * @return The join command builder.
   */
  private LiteralArgumentBuilder<CommandSourceStack> joinCommand() {
    return Commands.literal("join")
        .requires(
            source -> source.getSender() instanceof Player p && p.hasPermission("ctf.play.join"))
        .then(
            Commands.argument("team", StringArgumentType.word())
                .suggests(TEAM_SUGGESTIONS)
                .executes(
                    context -> {
                      Player player = (Player) context.getSource().getSender();

                      String teamArg = context.getArgument("team", String.class);
                      Optional<Team> team = parseTeamArgument(player, teamArg);
                      if (team.isEmpty()) return Command.SINGLE_SUCCESS;

                      if (gameManager.getState() != GameState.LOBBY) {
                        player.sendMessage(
                            MINI_MESSAGE.deserialize(
                                "<gray>There is currently a game in progress. You cannot join a team."));
                        return Command.SINGLE_SUCCESS;
                      }

                      Optional<Team> existingTeam = gameManager.getPlayerTeam(player.getUniqueId());
                      if (existingTeam.isPresent()) {
                        Team existing = existingTeam.get();
                        player.sendMessage(
                            MINI_MESSAGE.deserialize(
                                String.format(
                                    "<gray>You are already on the <%s>%s</%s> <gray>team. Use <white>/ctf leave</white> <gray>first.",
                                    existing.getColor(),
                                    existing.getDisplayName(),
                                    existing.getColor())));
                        return Command.SINGLE_SUCCESS;
                      }

                      Team teamToJoin = team.get();
                      gameManager.setPlayerTeam(player.getUniqueId(), teamToJoin);

                      player.sendMessage(
                          MINI_MESSAGE.deserialize(
                              String.format(
                                  "<gray>Joined the <%s>%s</%s> <gray>team.",
                                  teamToJoin.getColor(),
                                  teamToJoin.getDisplayName(),
                                  teamToJoin.getColor())));

                      player
                          .getServer()
                          .broadcast(
                              MINI_MESSAGE.deserialize(
                                  String.format(
                                      "<white>%s</white> <gray>has joined the <%s>%s</%s> <gray>team.",
                                      player.getName(),
                                      teamToJoin.getColor(),
                                      teamToJoin.getDisplayName(),
                                      teamToJoin.getColor())));
                      return Command.SINGLE_SUCCESS;
                    }));
  }

  /**
   * Creates the leave command.
   *
   * @return The leave command builder.
   */
  private LiteralArgumentBuilder<CommandSourceStack> leaveCommand() {
    return Commands.literal("leave")
        .requires(
            source -> source.getSender() instanceof Player p && p.hasPermission("ctf.play.join"))
        .executes(
            context -> {
              Player player = (Player) context.getSource().getSender();

              Optional<Team> currentTeam = gameManager.getPlayerTeam(player.getUniqueId());
              if (currentTeam.isEmpty()) {
                player.sendMessage(MINI_MESSAGE.deserialize("<gray>You are not in a team."));
                return Command.SINGLE_SUCCESS;
              }

              Team team = currentTeam.get();
              gameManager.dropFlagIfCarried(player, player.getLocation());
              gameManager.removePlayerTeam(player.getUniqueId());

              player.sendMessage(
                  MINI_MESSAGE.deserialize(
                      String.format(
                          "<gray>Left the <%s>%s</%s> <gray>team.",
                          team.getColor(), team.getDisplayName(), team.getColor())));

              player
                  .getServer()
                  .broadcast(
                      MINI_MESSAGE.deserialize(
                          String.format(
                              "<white>%s</white> <gray>has left the <%s>%s</%s> <gray>team.",
                              player.getName(),
                              team.getColor(),
                              team.getDisplayName(),
                              team.getColor())));

              return Command.SINGLE_SUCCESS;
            });
  }

  /**
   * Creates the setflag command.
   *
   * @return The flag command builder.
   */
  private LiteralArgumentBuilder<CommandSourceStack> setFlagCommand() {
    return Commands.literal("setflag")
        .requires(
            source -> source.getSender() instanceof Player p && p.hasPermission("ctf.admin.setup"))
        .then(
            Commands.argument("team", StringArgumentType.word())
                .suggests(TEAM_SUGGESTIONS)
                .executes(
                    context -> {
                      Player player = (Player) context.getSource().getSender();

                      String teamArg = context.getArgument("team", String.class);
                      Optional<Team> team = parseTeamArgument(player, teamArg);
                      if (team.isEmpty()) return Command.SINGLE_SUCCESS;

                      if (gameManager.getState() != GameState.LOBBY) {
                        player.sendMessage(
                            MINI_MESSAGE.deserialize(
                                "<gray>Flag positions cannot be changed while a game is in progress."));
                        return Command.SINGLE_SUCCESS;
                      }

                      Location location = player.getLocation();
                      if (location.getWorld() == null) {
                        player.sendMessage(
                            MINI_MESSAGE.deserialize(
                                "<gray>Unable to set flag: your current world could not be determined."));
                        return Command.SINGLE_SUCCESS;
                      }

                      Location blockLocation = location.toBlockLocation();

                      Team teamToSet = team.get();
                      Team oppositeTeam = teamToSet.getOpposite();

                      gameManager
                          .getTeamData(teamToSet)
                          .ifPresent(
                              data -> {
                                data.setBaseFlagLocation(blockLocation);
                                data.setCurrentFlagLocation(blockLocation);
                              });

                      String worldName = blockLocation.getWorld().getName();
                      int x = blockLocation.getBlockX();
                      int y = blockLocation.getBlockY();
                      int z = blockLocation.getBlockZ();
                      player.sendMessage(
                          MINI_MESSAGE.deserialize(
                              String.format(
                                  "<gray>The <%s>%s</%s> <gray>flag has been set at <white>%s</white> <gray>(%d, %d, %d).",
                                  teamToSet.getColor(),
                                  teamToSet.getDisplayName(),
                                  teamToSet.getColor(),
                                  worldName,
                                  x,
                                  y,
                                  z)));

                      boolean oppositeUnset =
                          gameManager
                              .getTeamData(oppositeTeam)
                              .flatMap(TeamData::getBaseFlagLocation)
                              .isEmpty();

                      if (oppositeUnset) {
                        player.sendMessage(
                            MINI_MESSAGE.deserialize(
                                String.format(
                                    "<gray>Reminder: the <%s>%s</%s> <gray>flag has not been set yet.",
                                    oppositeTeam.getColor(),
                                    oppositeTeam.getDisplayName(),
                                    oppositeTeam.getColor())));
                      }

                      return Command.SINGLE_SUCCESS;
                    }));
  }

  /**
   * Creates the start command.
   *
   * @return The start command builder.
   */
  private LiteralArgumentBuilder<CommandSourceStack> startCommand() {
    return Commands.literal("start")
        .requires(source -> source.getSender().hasPermission("ctf.admin.control"))
        .executes(
            context -> {
              CommandSender sender = context.getSource().getSender();
              Optional<String> error =
                  gameManager.startGame(
                      bossBarService,
                      message -> sender.getServer().broadcast(MINI_MESSAGE.deserialize(message)));
              error.ifPresent(msg -> sender.sendMessage(MINI_MESSAGE.deserialize(msg)));

              return Command.SINGLE_SUCCESS;
            });
  }

  /**
   * Creates the stop command.
   *
   * @return The stop command builder.
   */
  private LiteralArgumentBuilder<CommandSourceStack> stopCommand() {
    return Commands.literal("stop")
        .requires(source -> source.getSender().hasPermission("ctf.admin.control"))
        .executes(
            context -> {
              CommandSender sender = context.getSource().getSender();

              if (gameManager.getState() != GameState.RUNNING) {
                sender.sendMessage(
                    MINI_MESSAGE.deserialize("<gray>There is no game in progress to stop."));
                return Command.SINGLE_SUCCESS;
              }

              gameManager.stopGame();
              sender
                  .getServer()
                  .broadcast(
                      MINI_MESSAGE.deserialize(
                          "<gray>The current Capture the Flag game has been stopped."));
              return Command.SINGLE_SUCCESS;
            });
  }

  /**
   * Creates the score command.
   *
   * @return The score command builder.
   */
  private LiteralArgumentBuilder<CommandSourceStack> scoreCommand() {
    return Commands.literal("score")
        .requires(source -> source.getSender() instanceof Player)
        .executes(
            context -> {
              Player player = (Player) context.getSource().getSender();

              if (gameManager.getState() != GameState.RUNNING) {
                player.sendMessage(MINI_MESSAGE.deserialize("<gray>There is no active game."));
                return Command.SINGLE_SUCCESS;
              }

              Map<Team, TeamData> teams = gameManager.getTeams();
              TeamData redData = teams.get(Team.RED);
              TeamData blueData = teams.get(Team.BLUE);
              int redScore = redData == null ? 0 : redData.getScore();
              int blueScore = blueData == null ? 0 : blueData.getScore();

              player.sendMessage(
                  MINI_MESSAGE.deserialize(
                      String.format(
                          "<gray>Score: <%s>%s</%s> <white>%d</white> <gray>- <%s>%s</%s> <white>%d</white>",
                          Team.RED.getColor(),
                          Team.RED.getDisplayName(),
                          Team.RED.getColor(),
                          redScore,
                          Team.BLUE.getColor(),
                          Team.BLUE.getDisplayName(),
                          Team.BLUE.getColor(),
                          blueScore)));
              return Command.SINGLE_SUCCESS;
            });
  }

  /**
   * Parses a {@link Team} from a command argument string, sending an error message to the player
   * and returning empty if the argument is invalid.
   *
   * @param player The player to send the error message to.
   * @param teamArg The raw team argument string.
   * @return An {@link Optional} containing the parsed {@link Team}, or empty if invalid.
   */
  private Optional<Team> parseTeamArgument(Player player, String teamArg) {
    Optional<Team> team = Team.fromString(teamArg);
    if (team.isEmpty()) {
      String validTeams =
          Arrays.stream(TEAMS)
              .map(
                  t -> String.format("<%s>%s</%s>", t.getColor(), t.getDisplayName(), t.getColor()))
              .collect(Collectors.joining("<gray>, "));
      player.sendMessage(
          MINI_MESSAGE.deserialize(
              String.format("<gray>Invalid team. Choose from: %s<gray>.", validTeams)));
    }
    return team;
  }
}
