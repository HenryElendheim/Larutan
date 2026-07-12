package world.larutan.engine.event

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The payoff beats are the steps of the climb that pull time back to a watchable pace (§10.4). */
class EventKindTest {

    @Test
    fun payoffBeatsAreBirthsDeathsAndReachedGoals() {
        assertTrue(EventKind.BIRTH.isPayoff)
        assertTrue(EventKind.DEATH.isPayoff)
        assertTrue(EventKind.GOAL_ACHIEVED.isPayoff)
        assertTrue(EventKind.MILESTONE.isPayoff)
    }

    @Test
    fun ordinaryEventsAreNotPayoffBeats() {
        assertFalse(EventKind.SEASON_TURN.isPayoff)
        assertFalse(EventKind.COPED.isPayoff)
        assertFalse(EventKind.GOAL_FORMED.isPayoff)
    }
}
