package world.larutan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.larutan.app.SimulationViewModel
import world.larutan.app.ui.model.FollowedBeing
import world.larutan.app.ui.model.MomentView
import world.larutan.app.ui.model.PlaceMode
import world.larutan.app.ui.model.RosterEntry
import world.larutan.app.ui.model.RosterFilter
import world.larutan.app.ui.model.Settings
import world.larutan.app.ui.model.TimelineMomentView
import world.larutan.app.ui.model.UiState
import world.larutan.app.ui.theme.Clay
import world.larutan.app.ui.theme.Ember

/**
 * The whole screen: the map up top, the clock, and — the point of it all — the
 * inner life of whoever you're following, scrolling below.
 */
/** Which page of the app is up. Settings and the chronicle each get their own now. */
private enum class Screen { MAIN, SETTINGS, CHRONICLE, EDIT }

@Composable
fun LarutanApp(vm: SimulationViewModel) {
    val state: UiState by vm.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.MAIN) }

    // Larger text scales every sp size at once, so it lifts the whole app together.
    val base = LocalDensity.current
    val scaled = Density(base.density, base.fontScale * if (state.settings.largerText) 1.3f else 1f)

    CompositionLocalProvider(LocalDensity provides scaled) {
        when (screen) {
            Screen.MAIN -> MainScreen(
                state, vm,
                onSettings = { screen = Screen.SETTINGS },
                onChronicle = { screen = Screen.CHRONICLE },
                onEdit = { screen = Screen.EDIT },
            )
            Screen.SETTINGS -> SettingsScreen(state.settings, onChange = vm::updateSettings, onBack = { screen = Screen.MAIN })
            Screen.CHRONICLE -> ChronicleScreen(state.chronicle, onBack = { screen = Screen.MAIN })
            Screen.EDIT -> {
                val f = state.followed
                if (f != null && f.alive) {
                    EditScreen(f, vm, onBack = { screen = Screen.MAIN })
                } else {
                    PageScaffold("Nothing to shape", onBack = { screen = Screen.MAIN }) {
                        Text(
                            "There's no living being to shape just now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: UiState,
    vm: SimulationViewModel,
    onSettings: () -> Unit,
    onChronicle: () -> Unit,
    onEdit: () -> Unit,
) {
    var placeMode by remember { mutableStateOf(PlaceMode.BEING) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WorldBar(
            state,
            onSettings = onSettings,
            onChronicle = onChronicle,
            onSpawn = vm::spawnBeing,
            onUndo = vm::undo,
            onRedo = vm::redo,
        )
        state.moment?.let {
            MomentBanner(it, onOpen = vm::openMoment, onDismiss = vm::dismissMoment)
        }
        WorldView(
            world = state.world,
            beings = state.beings,
            map = state.map,
            onSelect = vm::follow,
            onPlace = { x, y ->
                when (placeMode) {
                    PlaceMode.BEING -> vm.spawnBeingAt(x, y)
                    PlaceMode.FOOD -> vm.growFoodAt(x, y)
                    PlaceMode.WATER -> vm.makeWaterAt(x, y)
                    PlaceMode.SHELTER -> vm.raiseShelterAt(x, y)
                }
            },
        )
        // Pick what a long-press on the map lays down -- a being, or a reshaping of the land.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceMode.entries.forEach { mode ->
                Chip(label = mode.label, selected = mode == placeMode) { placeMode = mode }
            }
        }
        Text(
            "Long-press the map to place ${placeMode.label}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TimeControls(
            current = state.speed,
            onSpeed = vm::setSpeed,
            onStep = vm::stepOnce,
        )

        // A god's reach over the whole living crowd at once.
        BulkPowers(onFeedAll = vm::provideAll, onWarmAll = vm::warmAll, onBlessAll = vm::blessAll)

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
            // The god's editing bench for whoever you're following.
            if (followed.alive) {
                NavPill("Shape ${followed.name}", onEdit)
            }
            InnerLifePanel(
                followed,
                onGod = vm::invoke,
                onReincarnate = vm::reincarnateFollowed,
                onDecreeFate = vm::decreeFate,
            )
        } else {
            Text(
                "No one left to follow. The world is quiet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun WorldBar(
    state: UiState,
    onSettings: () -> Unit,
    onChronicle: () -> Unit,
    onSpawn: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val w = state.world
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    if (w.harshSpell) "${w.timeOfDay} · ${w.weather} · a hard spell" else "${w.timeOfDay} · ${w.weather}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (w.harshSpell) Clay else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${w.population} living",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The two other pages, a god's power to make a new being, and an undo, one tap away.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavPill("New being", onSpawn)
            if (state.canUndo) NavPill("Undo", onUndo)
            if (state.canRedo) NavPill("Redo", onRedo)
            NavPill("Chronicle", onChronicle)
            NavPill("Settings", onSettings)
        }
    }
}

/** A god's blessings poured over the whole living crowd at once. */
@Composable
private fun BulkPowers(onFeedAll: () -> Unit, onWarmAll: () -> Unit, onBlessAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "OVER ALL THE LIVING",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavPill("Feed all", onFeedAll)
            NavPill("Warm all", onWarmAll)
            NavPill("Bless all", onBlessAll)
        }
    }
}

/** A small tappable pill used for moving between pages. */
@Composable
private fun NavPill(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A page header with a back arrow-free "Back" pill and a title. */
@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NavPill("Back", onBack)
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** A full page shell: a back header, then the page's content, scrolling. */
@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(title, onBack)
        content()
    }
}

@Composable
private fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    PageScaffold("Settings", onBack) {
        SettingsPanel(settings, onChange)
    }
}

@Composable
private fun ChronicleScreen(entries: List<String>, onBack: () -> Unit) {
    PageScaffold("Chronicle", onBack) {
        if (entries.isEmpty()) {
            Text(
                "Nothing has happened worth setting down yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EditScreen(b: FollowedBeing, vm: SimulationViewModel, onBack: () -> Unit) {
    PageScaffold("Shape ${b.name}", onBack) {
        Text(
            "True godhood: remake them as you please. Changes take at once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Name -------------------------------------------------------------
        EditLabel("Name")
        var nameField by remember(b.id) { mutableStateOf(b.name) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            NavPill("Rename") { vm.editName(nameField) }
        }

        // --- Colour -----------------------------------------------------------
        EditLabel("Colour")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.hsv(b.hue % 360f, 0.42f, 0.8f)),
            )
            Slider(
                value = b.hue.coerceIn(0f, 360f),
                onValueChange = { vm.editHue(it) },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f),
            )
        }

        // --- Size -------------------------------------------------------------
        LabeledSlider("Size", b.size, 0.4f..2.5f, { vm.editSize(it) }, "${(b.size * 100).toInt()}%")

        // --- Age --------------------------------------------------------------
        LabeledSlider(
            "Age", b.ageYears.toFloat(), 0f..90f, { vm.editAge(it) },
            "${b.ageYears} years · ${b.lifeStage}",
        )

        // --- Stats ------------------------------------------------------------
        EditLabel("Their needs, set outright")
        b.drives.forEach { d ->
            LabeledSlider(
                d.label.replaceFirstChar { it.uppercase() },
                d.value.coerceIn(0f, 1f),
                0f..1f,
                { vm.editDrive(d.label, it) },
                "${(d.value * 100).toInt()}",
            )
        }

        // --- The final power --------------------------------------------------
        Spacer(Modifier.height(8.dp))
        Text(
            "Strike down",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF1B1116), // near-black, so it reads on the clay
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Clay)
                .clickable { vm.smiteFollowed(); onBack() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EditLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueText: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingsPanel(settings: Settings, onChange: (Settings) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        SettingRow(
            "Slow down for big moments",
            "Off lets you blitz -- births, deaths and the rest won't ease the speed for you.",
            settings.slowForMoments,
        ) { onChange(settings.copy(slowForMoments = it)) }
        SettingRow(
            "Show moment banners",
            "The little note when something worth seeing happens.",
            settings.showMomentBanners,
        ) { onChange(settings.copy(showMomentBanners = it)) }
        SettingRow(
            "Larger text",
            "Scales everything up a little for easier reading.",
            settings.largerText,
        ) { onChange(settings.copy(largerText = it)) }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle(!on) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A plain on/off pill, filled when on -- readable without relying on colour alone.
        Text(
            if (on) "On" else "Off",
            style = MaterialTheme.typography.labelLarge,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
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

