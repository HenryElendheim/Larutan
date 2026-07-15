package world.larutan.engine.sim

import world.larutan.engine.world.Terrain
import world.larutan.engine.world.Tile
import world.larutan.engine.world.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shelter is the land's own cover plus what hands raise on it, and the built part lasts but weathers (§3.5). */
class ShelterTest {

    @Test
    fun shelterIsNaturalCoverPlusWhatsBuilt() {
        val t = Tile(Terrain.FOREST, food = 0.0, water = 0.0, materials = 0.0, naturalShelter = 0.3, built = 0.4)
        assertEquals(0.7, t.shelterQuality, 1e-9, "natural cover and built shelter add up")

        t.built = 0.9
        assertEquals(1.0, t.shelterQuality, 1e-9, "and the total never runs past full cover")
    }

    @Test
    fun aBuiltShelterWeathersWhenNoOneKeepsItUp() {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 4, seed = 3L)
        val (world, beings) = WorldGen.create(config)
        val sim = Simulation(config, world, beings)

        // A water tile no one builds on: its raised shelter can only weather down.
        val tile = world.tiles.first { it.terrain == Terrain.WATER }
        tile.built = 0.8
        sim.run(World.TICKS_PER_DAY) // a full day: every tile gets touched

        assertTrue(tile.built < 0.8, "left untended, the built shelter weathers back down")
    }
}
