# Larutan

Larutan is an Android life-simulation about a handful of small beings who live
their own lives in a 2D world. The point isn't the world -- it's their inner
life: their needs, feelings, memories, dreams, relationships, and the things
they slowly reach for. You pick one to follow and watch a life unfold, hour by
hour and season by season.

The name is "natural" spelled backwards: a world that behaves naturally, built
from the inside out.

## What it does

- Simulates a small group of beings whose behaviour comes from their own drives,
  personality, and memories -- nothing is scripted, so their lives surprise you.
- Shows you any being's whole inner life: what's pressing on them, how they feel,
  what they're working toward, what they think, and what they dreamed last night.
- Runs a grounded world of seasons and scarcity, where bonds form, goals are
  chased, generations are born, and lives eventually end and are remembered.

Dark-mode first, quiet, and made to sit with rather than grind through.

## How it's built

Two modules, following the plan's rule -- build the soul before the skin.

- `engine/` -- the whole simulation, in pure Kotlin with zero Android imports.
  World, time, seasons, food, beings (drives, personality, emotion, memory,
  relationships, goals), the utility-based decision loop, template thoughts and
  dreams, reproduction and inheritance, mortality, the chronicle, and a complete
  serializable snapshot (which is what save, load, and rewind all run on).
  Because it's plain Kotlin, it runs headless on a desktop as a console program.
- `app/` -- the Android app, in Jetpack Compose. It reads engine state and draws
  the 2D dot-grid world, the time controls, and the inner-life panel. The engine
  does the thinking; the app just paces it and shows it.

The layers never cross: the simulation core never knows how anything is drawn or
worded. That's what lets art and richer language come later without touching it.

## Running the headless engine

You can watch a world live entirely in text, no emulator needed:

```
./gradlew :engine:run                 # a season, following one being
./gradlew :engine:run --args="60 2"   # 60 world-days, follow being #2
```

It prints the world advancing, the followed being's thoughts, drives, goals and
dreams, and a chronicle of the significant moments -- births, bonds, milestones,
deaths.

## Building the app

Open the project in Android Studio and run the `app` configuration on a device
or emulator (minSdk 26). The engine is a plain Kotlin module the app depends on,
so the same simulation code runs on the phone that runs in the console.

## Status

This is the first cut. The engine core is in and runs end to end; the Compose UI
gives you the map, the clock, and the inner-life panel. The larger systems from
the design plan -- real conversations, the god layer, the afterlife, the rewind
UI, deeper settings -- build on top of this foundation from here.

## License

MIT. See [LICENSE](LICENSE).
