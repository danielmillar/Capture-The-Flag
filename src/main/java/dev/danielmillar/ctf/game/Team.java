package dev.danielmillar.ctf.game;

import java.util.Arrays;
import java.util.Optional;
import org.bukkit.Material;

public enum Team {
  RED("Red", "red", Material.RED_BANNER),
  BLUE("Blue", "blue", Material.BLUE_BANNER);

  private final String displayName;
  private final String color;
  private final Material bannerMaterial;

  Team(String displayName, String color, Material bannerMaterial) {
    this.displayName = displayName;
    this.color = color;
    this.bannerMaterial = bannerMaterial;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getColor() {
    return color;
  }

  public Material getBannerMaterial() {
    return bannerMaterial;
  }

  /**
   * Parses a {@link Team} from a string, case-insensitively.
   *
   * @param name The team name to parse.
   * @return An {@link Optional} containing the matched {@link Team}, or empty if none matched.
   */
  public static Optional<Team> fromString(String name) {
    return Arrays.stream(values()).filter(t -> t.name().equalsIgnoreCase(name)).findFirst();
  }

  /**
   * Gets the opposite team.
   *
   * @return {@link Team#BLUE} if this is {@link Team#RED}, and vice versa.
   */
  public Team getOpposite() {
    return this == RED ? BLUE : RED;
  }
}
