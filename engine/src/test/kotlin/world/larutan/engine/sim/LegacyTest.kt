package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import kotlin.test.Test
import kotlin.test.assertTrue

/** What the dying held most firmly passes to the one who loved them best (§9). */
class LegacyTest {

    private fun freshSim(seed: Long = 8L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun aFirmConvictionOutlivesTheOneWhoHeldIt() {
        val sim = freshSim()
        val elder = sim.beings[0]
        val heir = sim.beings[1]

        // The elder holds a firm, distinctive conviction; the heir loves them and does not.
        elder.hold(BeliefKind.THE_FAR_PLACES_CALL, 0.6, "a lifetime of wandering")
        heir.relationshipWith(elder.id).warm(70.0)
        assertTrue(heir.beliefs.none { it.kind == BeliefKind.THE_FAR_PLACES_CALL })

        // The elder is out of time.
        elder.ageYears = 85.0
        sim.run(3)

        assertTrue(!elder.alive, "the elder has died")
        assertTrue(
            heir.beliefs.any { it.kind == BeliefKind.THE_FAR_PLACES_CALL },
            "and what they believed lives on in the one who loved them best",
        )
    }
}
