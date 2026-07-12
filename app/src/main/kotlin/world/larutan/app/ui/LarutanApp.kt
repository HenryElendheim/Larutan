package world.larutan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.larutan.app.SimulationViewModel
import world.larutan.app.ui.model.MomentView
import world.larutan.app.ui.model.RosterEntry
import world.larutan.app.ui.model.RosterFilter
import world.larutan.app.ui.model.TimelineMomentView
import world.larutan.app.ui.model.UiState
import world.larutan.app.ui.theme.Ember

/**
 * The whole screen: the map up top, the clock, and — the point of it all — the
 * inner life of whoever you're following, scrolling below.
 */
@Composable
fun LarutanApp(vm: SimulationViewModel) {
    val state: UiState by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WorldBar(state)
        state.moment?.let {
            MomentBanner(it, onOpen = vm::openMoment, onDismiss = vm::dismissMoment)
        }
        WorldView(
            world = state.world,
            beings = state.beings,
            onSelect = vm::follow,
        )
        TimeControls(
            current = state.speed,
            onSpeed = vm::setSpeed,
            onStep = vm::stepOnce,
        )

        if (state.timeline.isNotEmpty()) {
            TimelineStrip(state.timeline, onRewind = vm::rewindTo)
        }

        RosterControls(
            filter = state.rosterFilter,
            realmFilter = state.realmFilter,
            onFilter = vm::setRosterFilter,
            onRealm = vm::setRealmFilter,
        )
        if (state.roster.isNotEmpty()) {
            Roster(state.roster, onSelect = vm::follow)
        } else {
            Text(
                if (state.rosterFilter == RosterFilter.DEAD) "None have passed here yet." else "No one is living.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(2.dp))

        val followed = state.followed
        if (followed != null) {
            InnerLifePanel(followed, onGod = vm::invoke, onReincarnate = vm::reincarnateFollowed)
        } else {
            Text(
                "No one left to follow. The world is quiet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }

        if (state.chronicle.isNotEmpty()) {
            Chronicle(state.chronicle)
        }
    }
}

@Composable
private fun WorldBar(state: UiState) {
    val w = state.world
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Larutan", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Year ${w.year} · ${w.season} · day ${w.dayOfSeason} of ${w.daysPerSeason}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${w.timeOfDay} · ${w.weather}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${w.population} living",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RosterControls(
    filter: RosterFilter,
    realmFilter: String?,
    onFilter: (RosterFilter) -> Unit,
    onRealm: (String?) -> Unit,
) {
    // Choose the crowd: the living, or the dead — and when it's the dead, which realm.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RosterFilter.entries.forEach { f ->
                Chip(label = f.label, selected = f == filter) { onFilter(f) }
            }
        }
        if (filter == RosterFilter.DEAD) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(label = "All", selected = realmFilter == null) { onRealm(null) }
                // Realm labels must match what the engine writes on a soul.
                listOf("Heaven", "Purgatory", "Hell").forEach { realm ->
                    Chip(label = realm, selected = realmFilter == realm) { onRealm(realm) }
                }
            }
        }
    }
}

@Composable
private fun Roster(entries: List<RosterEntry>, onSelect: (Int) -> Unit) {
    // Pick whom to follow -> you can follow the dead too, so every card is selectable.
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { e ->
            val selected = e.selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(e.id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    e.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        e.alive -> MaterialTheme.colorScheme.onBackground
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    // For the dead, name the realm they settled in alongside how they were lost.
                    if (e.realm != null) "${e.note} · ${e.realm}" else e.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun MomentBanner(moment: MomentView, onOpen: () -> Unit, onDismiss: () -> Unit) {
    // Something worth seeing just happened. Tap it to go to whoever it happened to;
    // dismiss to wave it away. It lingers until the next moment or you clear it.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable { onOpen() },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "A MOMENT",
                style = MaterialTheme.typography.labelSmall,
                color = Ember,
                fontWeight = FontWeight.Medium,
            )
            Text(moment.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            "Dismiss",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(start = 12.dp),
        )
    }
}

@Composable
private fun TimelineStrip(moments: List<TimelineMomentView>, onRewind: (Long) -> Unit) {
    // Reverse time by drilling in: pick a year, then a month, a week, a day, and
    // finally a time of day -> then the world rolls back to exactly that moment.
    // Each level narrows the ones below it.
    var year by remember { mutableStateOf<Long?>(null) }
    var month by remember { mutableStateOf<Int?>(null) }
    var week by remember { mutableStateOf<Int?>(null) }
    var day by remember { mutableStateOf<Int?>(null) }

    fun back() {
        when {
            day != null -> day = null
            week != null -> week = null
            month != null -> month = null
            year != null -> year = null
        }
    }

    val here = moments.filter {
        (year == null || it.year == year) &&
            (month == null || it.monthIndex == month) &&
            (week == null || it.week == week) &&
            (day == null || it.dayOfSeason == day)
    }

    val crumbs = buildList {
        if (year != null) add("Year $year")
        if (month != null) add(here.firstOrNull()?.monthLabel?.replaceFirstChar { it.uppercase() } ?: "")
        if (week != null) add("Week ${week!! + 1}")
        if (day != null) add("Day ${day!! + 1}")
    }
    val heading = when {
        day != null -> "choose a time"
        week != null -> "choose a day"
        month != null -> "choose a week"
        year != null -> "choose a month"
        else -> "choose a year"
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "REWIND",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            if (year != null) Chip(label = "Back", selected = false) { back() }
            Text(
                if (crumbs.isEmpty()) heading else crumbs.joinToString(" · ") + " · $heading",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                year == null -> here.map { it.year }.distinct().sorted().forEach { y ->
                    Chip(label = "Year $y", selected = false) { year = y }
                }
                month == null -> here.distinctBy { it.monthIndex }.sortedBy { it.monthIndex }.forEach { m ->
                    Chip(label = m.monthLabel.replaceFirstChar { it.uppercase() }, selected = false) { month = m.monthIndex }
                }
                week == null -> here.map { it.week }.distinct().sorted().forEach { w ->
                    Chip(label = "Week ${w + 1}", selected = false) { week = w }
                }
                day == null -> here.map { it.dayOfSeason }.distinct().sorted().forEach { d ->
                    Chip(label = "Day ${d + 1}", selected = false) { day = d }
                }
                else -> here.sortedBy { it.tick }.forEach { m ->
                    // The last level: pick the time of day, and time reverses to it.
                    Chip(label = m.timeLabel, selected = m.isNow) {
                        onRewind(m.tick)
                        year = null; month = null; week = null; day = null
                    }
                }
            }
        }
    }
}

@Composable
private fun Chronicle(entries: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "THE CHRONICLE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        entries.forEach {
            Text("— $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
