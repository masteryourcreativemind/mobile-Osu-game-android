# Osu Tiles

A simple Android rhythm game in the spirit of **osu!** and **Piano Tiles**: tap
hit circles in time with the music. Every song is turned into a playable map
automatically, so you can play *any* track — including ones you add yourself.

## Features

- **osu!-style gameplay** — approaching hit circles with shrinking approach
  rings, combo numbers, timing judgements (300 / 100 / 50 / miss), combo
  multiplier, live accuracy and an end-of-song grade (SS…D).
- **Play the song, not a fixed chart** — maps are generated from the audio itself
  via energy-based onset detection, so notes land on the actual beats/hits you
  hear.
- **Four difficulties straight from the osu! playbook** — Easy, Normal, Hard,
  Insane. Harder difficulties keep more of the song's onsets, shrink the approach
  time and circle size, and tighten the hit windows. Same song, more challenge.
- **Community-driven, no restrictions** — add any audio file from your device via
  the system file picker. No limits on size or format; whatever Android can
  decode gets an onset-based map, and anything else falls back to a steady
  rhythm so it is always playable.
- **Built-in demo track** — ships with *Neon Pulse*, a royalty-free,
  procedurally-composed Western-pop (I–V–vi–IV) tune so the game is playable out
  of the box. No copyrighted or region-specific content.
- **Local-only today, peer-to-peer ready** — see below.

## Peer-to-peer map sharing (ready to enable)

The app is architected for a **peer-to-peer swarm** (BitTorrent-style) rather than
a central server — users share maps directly with each other. It is fully
local-only right now; the networking seam is in place and inert until switched on.

- `backend/MapSource` — the single interface the app uses for any song provider.
- `backend/LocalMapSource` — the always-on, on-device library.
- `backend/PeerMapSource` — aggregates catalogs from discovered peers and lets you
  download maps directly from whoever is seeding them.
- `backend/PeerDiscovery` / `PeerTransport` — pluggable LAN (NSD/mDNS) + DHT/tracker
  discovery and data transfer.
- `backend/P2PConfig` + `SeedingPolicy` — the master switch and the user's seeding
  preferences:
  - **seed maps you create/import** so others can get them;
  - **re-seed maps you download** (keep popular content alive even if the creator
    goes offline);
  - optionally **only re-seed high-demand maps** above a configurable threshold,
    with Wi-Fi-only and storage limits.
- `backend/SeedingManager` — pure, tested decision logic for *what* to seed.

To bring the swarm online, implement `PeerDiscovery`/`PeerTransport`, call
`MapSourceRegistry.installP2P(...)`, and set `P2PConfig.enabled = true`. No
gameplay or UI changes are required.

## Project layout

```
app/src/main/java/com/osutiles/game/
  model/      Difficulty, HitObject, Beatmap, Song  (pure data)
  audio/      PcmDecoder                            (decode any audio to PCM)
  beatmap/    OnsetDetector, BeatmapGenerator       (audio -> playable map)
  game/       GameView, GameActivity, Scoreboard    (rendering, input, scoring)
  repository/ SongRepository, LocalSongRepository, SongImporter
  backend/    MapSource + peer-to-peer swarm plumbing
  ui/         MainActivity, DifficultyActivity, ResultActivity, SongAdapter
```

The gameplay-critical logic (onset detection, map generation, scoring and the
seeding policy) is kept free of Android dependencies and covered by JVM unit
tests in `app/src/test/`.

## Building

Open the project in Android Studio (or build from the CLI) with the Android SDK
installed:

```
./gradlew assembleDebug      # build the APK
./gradlew test               # run the JVM unit tests
```

- Kotlin, Android Gradle Plugin 8.5.2, `compileSdk` 34, `minSdk` 24.
