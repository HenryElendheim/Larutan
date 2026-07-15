package world.larutan.engine.sim

import world.larutan.engine.Rng
import world.larutan.engine.being.Being
import world.larutan.engine.being.Personality
import world.larutan.engine.world.Terrain
import world.larutan.engine.world.Tile
import world.larutan.engine.world.World
import kotlinx.serialization.Serializable

/** How a fresh world is set up, before any life begins. */
@Serializable
data class WorldConfig(
    val width: Int = 40,
    val height: Int = 40,
    val startingPopulation: Int = 5,
    val seed: Long = 20260711L,
    val atypicalChance: Double = 0.12,
    val harshness: Double = 1.0, // scales starting scarcity; 1.0 is the default world
)

object WorldGen {

    fun create(config: WorldConfig): Pair<World, MutableList<Being>> {
        val rng = Rng(config.seed)
        val world = buildWorld(config, rng)
        val beings = buildBeings(config, world, rng)
        return world to beings
    }

    private fun buildWorld(config: WorldConfig, rng: Rng): World {
        val tiles = ArrayList<Tile>(config.width * config.height)
        for (y in 0 until config.height) {
            for (x in 0 until config.width) {
                val terrain = rollTerrain(rng)
                val abundance = 1.0 / config.harshness.coerceAtLeast(0.3)
                tiles += Tile(
                    terrain = terrain,
                    food = when (terrain) {
                        Terrain.GRASS -> rng.nextDoubleRange(20.0, 55.0) * abundance
                        Terrain.FOREST -> rng.nextDoubleRange(40.0, 95.0) * abundance
                        else -> 0.0
                    },
                    water = if (terrain == Terrain.WATER) 100.0 else 0.0,
                    materials = when (terrain) {
                        Terrain.FOREST -> rng.nextDoubleRange(20.0, 70.0)
                        Terrain.ROCK -> rng.nextDoubleRange(40.0, 100.0)
                        else -> 0.0
                    },
                    naturalShelter = when (terrain) {
                        Terrain.SHELTER -> rng.nextDoubleRange(0.6, 1.0)
                        Terrain.FOREST -> rng.nextDoubleRange(0.15, 0.4)
                        Terrain.ROCK -> rng.nextDoubleRange(0.25, 0.5)
                        else -> 0.0
                    },
                )
            }
        }
        return World(config.width, config.height, tiles)
    }

    private fun rollTerrain(rng: Rng): Terrain {
        val r = rng.nextDouble()
        return when {
            r < 0.50 -> Terrain.GRASS
            r < 0.72 -> Terrain.FOREST
            r < 0.84 -> Terrain.WATER
            r < 0.94 -> Terrain.ROCK
            else -> Terrain.SHELTER
        }
    }

    private fun buildBeings(config: WorldConfig, world: World, rng: Rng): MutableList<Being> {
        val beings = mutableListOf<Being>()
        // Start the little group near the middle so they can find each other.
        val cx = world.width / 2
        val cy = world.height / 2
        for (i in 0 until config.startingPopulation) {
            val x = (cx + rng.nextIntRange(-4, 4)).coerceIn(0, world.width - 1)
            val y = (cy + rng.nextIntRange(-4, 4)).coerceIn(0, world.height - 1)
            beings += Being(
                id = i,
                name = Names.random(rng),
                x = x,
                y = y,
                personality = Personality.random(rng, config.atypicalChance),
                generation = 1,
                ageYears = rng.nextDoubleRange(16.0, 30.0),
                appearanceSeed = rng.nextInt(360),
            )
        }
        return beings
    }
}
