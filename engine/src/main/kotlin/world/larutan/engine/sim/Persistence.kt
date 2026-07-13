package world.larutan.engine.sim

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import world.larutan.engine.Rng
import world.larutan.engine.being.Being
import world.larutan.engine.event.Chronicle
import world.larutan.engine.god.Fate
import world.larutan.engine.world.World

/**
 * A complete, serializable snapshot of a world at one moment. This is the single
 * mechanism behind save/load, autosave, and the rewind timeline (§10.5) — build
 * clean serializable state from the start and reversing time comes almost for free.
 */
@Serializable
data class WorldState(
    val config: WorldConfig,
    val world: World,
    val beings: List<Being>,
    val chronicle: Chronicle,
    val rng: Rng,
    val fates: List<Fate> = emptyList(), // waiting intentions ride along, so rewind arms them again
)

object Persistence {
    val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun snapshot(sim: Simulation): WorldState =
        WorldState(sim.config, sim.world, sim.beings.toList(), sim.chronicle, Rng(sim.rng.state), sim.fates.toList())

    fun serialize(state: WorldState): String = json.encodeToString(state)

    fun deserialize(text: String): WorldState = json.decodeFromString(text)

    /** Rebuild a runnable simulation from a snapshot. */
    fun restore(state: WorldState): Simulation = Simulation(
        config = state.config,
        world = state.world,
        beings = state.beings.toMutableList(),
        rng = state.rng,
        chronicle = state.chronicle,
        fates = state.fates.toMutableList(),
    )
}
