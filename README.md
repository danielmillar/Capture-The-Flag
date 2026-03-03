# Capture The Flag

## Tested On

- **Paper** `1.21.11` (build `#126`)
- **Java** `21`

---

## Build and Run

```bash
./gradlew build
```

The compiled jar is output to `build/libs/capturetheflag-<version>.jar`.

```bash
./gradlew runServer
```

You can run a local server directly from Gradle with the latest version of Paper.

---

## Quick Start

1. **Set flag bases:** Stand at each base location and run:
   ```
   /ctf setflag red
   /ctf setflag blue
   ```
2. **Players join teams:** Each team must have one player online to start the match.
   ```
   /ctf join red
   /ctf join blue
   ```
3. **Start the match:**
   ```
   /ctf start
   ```

The first team to **3 captures** wins. If no team reaches three captures, the match ends after **10 minutes** and the team with the higher score wins.

---

## Gameplay

- **Pick up:** Right-click the enemies flag to pick it up.
- **Drop:** The flag you picked up will be dropped at your location if you die or disconnect.
- **Return:** Right-click your own flag to return it to its base.
- **Capture:** Bring the enemy flag to your base to capture it and score.
- **Win:** First team to **3 captures** wins or the match ends after **10 minutes** and the team with the higher score wins.

---

## Future Considerations

- **Unit Testing:** The plugin has been tested manually, but I would like to add unit tests to cover as many edge cases as possible, as manual testing is not always reliable. There are definitely known edge cases that I have not covered, such as where to drop the flag if the player is mid-air.
- **Performance:** In terms of performance, there are a few places where I would consider optimizing the code. For example, the player move event is fired a lot so would benefit from early returns to reduce processing. Another one is that BossBarService that it loops all online players, which could be improved by showing and hiding during join/quit events. There will be other areas where I can improve performance, but I just mentioned a few.
- **Message Formatting:** I currently use MiniMessage for chat formatting, but for placeholders I use `String.format` for simplicity whereas it would be better to use MiniMessage's TagResolver for more flexibility.
- **Map Interactions:** I've only considered block placing/breaking interactions in terms of preserving the maps integrity. This would need to be expanded to include other interactions before this would be a complete mini-game.
- **Sound/Title Feedback:** Right now there is only chat and boss bar feedback. I believe that adding sounds and titles to specific actions would make it more engaging and overall a better experience. For example, adding sounds when players pick up a flag or when a team scores.
- **Configuration:** For simplicity, all configuration in terms of score to win, match duration and flag locations are all stored in memory and not persisted. Persistence would be required for a production-ready version where we need to create multiple instances of games with ease.
- **Gameplay Rules:** Based on some research, a normal rule of capture the flag in some cases is to only allow captures of the enemy flag if your own flag is still at your base. In this case, I decided to not implement this rule to keep the gameplay simple and not overcomplicate it.
