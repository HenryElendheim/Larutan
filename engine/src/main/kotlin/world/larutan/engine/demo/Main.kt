package world.larutan.engine.demo

import world.larutan.engine.being.DriveType
import world.larutan.engine.sim.Persistence
import world.larutan.engine.sim.Simulation
import world.larutan.engine.sim.WorldConfig
import world.larutan.engine.sim.WorldGen
import world.larutan.engine.world.World

/**
 * Larutan, headless. Runs a small world for a stretch of time and prints what
 * unfolds — the soul before the skin. No emulator, no UI: just lives, in text.
 *
 *   ./gradlew :engine:run                (default: a season, following one being)
 *   ./gradlew :engine:run --args="8 2"   (8 world-days, follow being #2)
 */
fun main(args: Array<String>) {
    val days = args.getOrNull(0)?.toIntOrNull() ?: 12
    val followId = args.getOrNull(1)?.toIntOrNull() ?: 0

    val config = WorldConfig(width = 36, height = 36, startingPopulation = 5, seed = 20260711L)
    val (world, beings) = WorldGen.create(config)
    val sim = Simulation(config, world, beings)

    banner("LARUTAN")
    println("A ${world.width}x${world.height} world. ${beings.size} beings, generation 1.\n")
    for (b in beings) {
        val tag = if (b.personality.isAtypical) "  (a mind unlike its peers)" else ""
        println("  ${b.name}  ${describe(b.personality)}$tag")
    }
    println()

    val totalTicks = days * World.TICKS_PER_DAY
    var lastDay = -1L
    repeat(totalTicks) {
        sim.step()
        val follow = sim.byId(followId)
        if (world.day != lastDay) {
            lastDay = world.day
            printDayHeader(world)
            if (follow != null && follow.alive) printFollow(sim, follow)
        }
    }

    banner("The chronicle so far")
    for (e in sim.chronicle.significant(24)) {
        println("  [y${e.tick / (World.DAYS_PER_SEASON * 4 * World.TICKS_PER_DAY)}] ${e.text}")
    }

    // Prove the whole world round-trips through serialization (save / load / rewind share this).
    val saved = Persistence.serialize(Persistence.snapshot(sim))
    val restored = Persistence.restore(Persistence.deserialize(saved))
    println()
    println("Snapshot: ${saved.length} chars, ${restored.beings.size} beings restored, tick ${restored.world.tick}.")
    println("Save / load / rewind all run on this one mechanism.")
}

private fun printDayHeader(world: World) {
    println("── Year ${world.year}, ${world.season.label}, day ${world.dayOfSeason + 1} — ${world.weather.label} ─".padEnd(64, '─'))
}

private fun printFollow(sim: Simulation, b: world.larutan.engine.being.Being) {
    println("Following ${b.name}: ${b.emotion.moodLabel()}, ${b.currentAction}. ${bars(b)}")
    b.goal?.let { g ->
        val done = g.milestones.count { m -> m.reached }
        println("   Goal: ${g.target}  [$done/${g.milestones.size} milestones, ${(g.progress * 100).toInt()}%]")
    }
    b.lastThought?.let { println("   \"$it\"") }
    b.lastDream?.let { if (sim.world.isNight || sim.world.timeOfDay.name == "DAWN") println("   (dream) $it") }
}

private fun bars(b: world.larutan.engine.being.Being): String {
    fun bar(d: DriveType): String {
        val v = (b.drives[d] / 10).toInt().coerceIn(0, 10)
        return d.label.take(4) + " " + "#".repeat(v) + "·".repeat(10 - v)
    }
    return listOf(DriveType.HUNGER, DriveType.WARMTH, DriveType.CONNECTION, DriveType.PURPOSE)
        .joinToString("  ") { bar(it) }
}

private fun describe(p: world.larutan.engine.being.Personality): String {
    val traits = buildList {
        if (p.warmth > 0.3) add("warm") else if (p.warmth < -0.3) add("aloof")
        if (p.boldness > 0.3) add("bold") else if (p.boldness < -0.3) add("cautious")
        if (p.curiosity > 0.3) add("curious")
        if (p.industry > 0.3) add("driven") else if (p.industry < -0.3) add("idle")
        if (p.optimism > 0.3) add("hopeful") else if (p.optimism < -0.3) add("downcast")
    }
    return if (traits.isEmpty()) "even-keeled" else traits.joinToString(", ")
}

private fun banner(title: String) {
    println()
    println("═".repeat(64))
    println("  $title")
    println("═".repeat(64))
}
