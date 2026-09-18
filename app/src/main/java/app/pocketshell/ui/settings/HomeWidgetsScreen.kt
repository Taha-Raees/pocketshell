package app.pocketshell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightRadioRow
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.widget.WidgetRegistry

/**
 * M8 — Control Center → Home widgets. The MINIMUM management surface:
 * pick the widget for each of the two Home hero slots, restore the
 * defaults. Not a marketplace: installation of optional catalog widgets is
 * a future milestone (docs/M8-WIDGET-SYSTEM.md §7); the picker lists what
 * is actually installed (today: the built-ins).
 */
@Composable
fun HomeWidgetsScreen(
    viewModel: app.pocketshell.widget.HomeWidgetsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()

    MidnightPageScaffold(title = "Home widgets", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The two hero cards on Home are slots. Each one renders " +
                    "the widget you assign to it; the defaults keep the " +
                    "Terminal and Linux cards.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))

            SlotPicker(slotLabel = "Slot 1", currentId = slots.getOrNull(0), onAssign = { viewModel.assignSlot(0, it) })
            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))
            SlotPicker(slotLabel = "Slot 2", currentId = slots.getOrNull(1), onAssign = { viewModel.assignSlot(1, it) })
            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            TextButton(
                onClick = { viewModel.restoreDefaults() },
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Text("Restore defaults", color = HomeTokens.accent)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SlotPicker(
    slotLabel: String,
    currentId: String?,
    onAssign: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MidnightSectionLabel(slotLabel)
        Spacer(Modifier.height(4.dp))
        WidgetRegistry.specs.forEach { spec ->
            MidnightRadioRow(
                selected = currentId == spec.id,
                label = spec.name,
                sublabel = spec.summary,
                onClick = { onAssign(spec.id) },
            )
        }
    }
}
