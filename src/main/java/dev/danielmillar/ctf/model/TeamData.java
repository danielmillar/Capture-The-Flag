package dev.danielmillar.ctf.model;

import dev.danielmillar.ctf.game.FlagState;
import java.util.*;
import org.bukkit.Location;

public class TeamData {

  private final Set<UUID> members;
  private Location baseFlagLocation;
  private Location currentFlagLocation;
  private UUID flagCarrier;
  private FlagState flagState;
  private int score;

  public TeamData() {
    this.members = new HashSet<>();
    this.score = 0;
    this.flagState = FlagState.AT_BASE;
  }

  public Set<UUID> getMembers() {
    return Collections.unmodifiableSet(members);
  }

  public boolean addPlayer(UUID uuid) {
    return members.add(uuid);
  }

  public boolean removePlayer(UUID uuid) {
    return members.remove(uuid);
  }

  public boolean hasMember(UUID uuid) {
    return members.contains(uuid);
  }

  public int getScore() {
    return score;
  }

  public int incrementScore() {
    return ++score;
  }

  public Optional<UUID> getFlagCarrier() {
    return Optional.ofNullable(flagCarrier);
  }

  public void setFlagCarrier(UUID uuid) {
    this.flagCarrier = uuid;
    this.flagState = FlagState.CARRIED;
  }

  public void clearFlagCarrier() {
    this.flagCarrier = null;
  }

  public FlagState getFlagState() {
    return flagState;
  }

  public void setFlagState(FlagState flagState) {
    this.flagState = flagState;
  }

  public Optional<Location> getBaseFlagLocation() {
    return Optional.ofNullable(baseFlagLocation);
  }

  public void setBaseFlagLocation(Location location) {
    this.baseFlagLocation = location;
  }

  public Optional<Location> getCurrentFlagLocation() {
    return Optional.ofNullable(currentFlagLocation);
  }

  public void setCurrentFlagLocation(Location location) {
    this.currentFlagLocation = location;
  }

  /**
   * Resets all mutable game states (score, carrier, flag state, and current flag position) back to
   * their base values. Members and base location are preserved.
   */
  public void reset() {
    this.score = 0;
    this.flagCarrier = null;
    this.flagState = FlagState.AT_BASE;
    this.currentFlagLocation = baseFlagLocation;
  }

  /** Resets per-round state while preserving score and base location. */
  public void resetForRound() {
    this.flagCarrier = null;
    this.flagState = FlagState.AT_BASE;
    this.currentFlagLocation = baseFlagLocation;
  }
}
