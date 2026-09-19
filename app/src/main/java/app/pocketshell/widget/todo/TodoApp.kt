package app.pocketshell.widget.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.HomeAppContext
import app.pocketshell.widget.HomeApplication
import app.pocketshell.widget.HomeAppSpec
import kotlinx.coroutines.launch

/**
 * M8.4 — TODO: a PocketShell Home Application answering, in one card,
 * "what do I need to do today?" with zero friction:
 *
 *   type into the inline field + enter → the task exists (the shared
 *   PocketShell keyboard deck is the input; no dialog, no screen).
 *   tap the box → done.  ☆ → starred (sorts first).  ARCHIVE → history.
 *
 * Design decisions (pinned by the todo test suite):
 *   - LOCAL-FIRST: the store is the app's own "todo_store" DataStore on
 *     the Android side — the card works before the Linux runtime is
 *     ready and needs no guest, no network, no account. It performs no
 *     navigation and polls nothing: reads are a DataStore flow (emits on
 *     change only), writes are DataStore `edit` transactions on its IO
 *     executor.
 *   - ARCHIVE, not delete: completed tasks can be archived (v1 has no
 *     hard delete — history survives until the list cap), and ARCHIVED
 *     is reachable in-card with RESTORE back to its prior state.
 *   - The store is a plain JSON array of [TodoTask] under one string key
 *     — a future CLI/agent integration reads and writes the same shape.
 *   - Sections are IN-CARD TABS (Today / Done / Archived), not depth —
 *     there is no detail page, so the card adds no BackHandler (system
 *     back keeps belonging to Home).
 *   - Persistence split: the task LIST lives in the DataStore (survives
 *     process death, rotation and carousel swipes); the active section
 *     and in-progress input text are `rememberSaveable`.
 *   - The card works regardless of runtime state — no Unavailable state
 *     exists, and none is invented.
 */
object TodoApp : HomeApplication() {

    const val ID = "todo"

    override val spec = HomeAppSpec(
        id = ID,
        name = "Todo",
        summary = "Today's tasks — add, complete, star; everything stays on this device",
    )

    @Composable
    override fun Content(context: HomeAppContext) {
        val appContext = LocalContext.current.applicationContext
        val repository = remember { TodoRepository(appContext) }
        // null until DataStore's first emission — the honest loading state.
        val tasksState by repository.tasks.collectAsState(initial = null)
        // Section + draft text are UI state: saveable across rotation,
        // Home↔Settings round-trips and carousel swipes.
        var sectionName by rememberSaveable { mutableStateOf(TodoSection.TODAY.name) }
        var input by rememberSaveable { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = TodoLayout.from(maxWidth.value, maxHeight.value)
            TodoContent(
                tasks = tasksState,
                section = TodoSection.from(sectionName),
                input = input,
                layout = layout,
                onSection = { sectionName = it.name },
                onInput = { input = it },
                onSubmit = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        input = ""
                        scope.launch { repository.add(text) }
                    }
                },
                onToggleDone = { id -> scope.launch { repository.toggleDone(id) } },
                onToggleStar = { id -> scope.launch { repository.toggleStar(id) } },
                onArchive = { id -> scope.launch { repository.setArchived(id, archived = true) } },
                onRestore = { id -> scope.launch { repository.setArchived(id, archived = false) } },
            )
        }
    }
}

/** The card's sections — in-card tabs, never navigation depth. */
internal enum class TodoSection(val label: String) {
    TODAY("TODAY"),
    DONE("DONE"),
    ARCHIVED("ARCHIVED");

    companion object {
        fun from(name: String?): TodoSection =
            entries.firstOrNull { it.name == name } ?: TODAY
    }
}

/**
 * The responsive contract — the same shape and inner-dp thresholds as
 * the reference ServersLayout: COMPACT caps rows at two plus "+N more"
 * (the card carries an input + tab row its siblings lack, so the honest
 * row budget is two); ROOMY adds the footer statistics and scrolling.
 * Pure + JVM-tested (TodoLayoutTest).
 */
internal enum class TodoLayout(
    val showsFooter: Boolean,
    val scrollsRows: Boolean,
) {
    COMPACT(showsFooter = false, scrollsRows = false),
    ROOMY(showsFooter = true, scrollsRows = true);

    val maxRows: Int get() = if (this == ROOMY) Int.MAX_VALUE else 2

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f

        fun from(widthDp: Float, heightDp: Float): TodoLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

@Composable
private fun TodoContent(
    tasks: List<TodoTask>?,
    section: TodoSection,
    input: String,
    layout: TodoLayout,
    onSection: (TodoSection) -> Unit,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleDone: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    val today = tasks?.let(TodoTasks::today).orEmpty()
    val done = tasks?.let(TodoTasks::done).orEmpty()
    val archived = tasks?.let(TodoTasks::archived).orEmpty()
    val list = when (section) {
        TodoSection.TODAY -> today
        TodoSection.DONE -> done
        TodoSection.ARCHIVED -> archived
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header — the application's title bar, both densities.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = "Todo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (today.isNotEmpty()) {
                Text(
                    text = "${today.size} open",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                )
            }
        }

        // The inline add field — one tap + type + enter. Always on top,
        // in every section, so adding never costs a navigation.
        Spacer(Modifier.height(2.dp))
        AddTaskField(
            input = input,
            onInput = onInput,
            onSubmit = onSubmit,
        )
        HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))

        // Section tabs — counts are real, per section, always honest.
        Spacer(Modifier.height(3.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TodoSection.entries.forEach { s ->
                val count = when (s) {
                    TodoSection.TODAY -> today.size
                    TodoSection.DONE -> done.size
                    TodoSection.ARCHIVED -> archived.size
                }
                val active = s == section
                Text(
                    text = if (count > 0) "${s.label} $count" else s.label,
                    fontFamily = TerminalTheme.mono,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = if (active) HomeTokens.accent else HomeTokens.textDim,
                    modifier = Modifier
                        .clickable(role = Role.Tab) { onSection(s) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Rows — capped + "+N more" in COMPACT, scrolling in ROOMY.
        val visible = list.take(layout.maxRows)
        if (visible.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (layout.scrollsRows) Modifier.verticalScroll(rememberScrollState())
                        else Modifier,
                    ),
            ) {
                visible.forEach { task ->
                    TodoRow(
                        task = task,
                        section = section,
                        onToggleDone = { onToggleDone(task.id) },
                        onToggleStar = { onToggleStar(task.id) },
                        onArchive = { onArchive(task.id) },
                        onRestore = { onRestore(task.id) },
                    )
                }
                if (list.size > visible.size) {
                    Text(
                        text = "+${list.size - visible.size} more",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = HomeTokens.textDim,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Footer statistics — roomy cards only.
        if (layout.showsFooter && tasks != null) {
            Text(
                text = "${today.size} OPEN · ${done.size} DONE · ${archived.size} ARCHIVED",
                fontFamily = TerminalTheme.mono,
                fontSize = 10.sp,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(2.dp))
        // The honest state line, every density, every theme.
        Text(
            text = when {
                tasks == null -> "…"
                section == TodoSection.TODAY && today.isEmpty() -> "Add your first task above"
                section == TodoSection.TODAY -> "${today.size} open"
                section == TodoSection.DONE && done.isEmpty() -> "Nothing completed yet"
                section == TodoSection.DONE -> "${done.size} completed"
                archived.isEmpty() -> "Nothing archived"
                else -> "${archived.size} archived"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (list.isNotEmpty()) HomeTokens.accent else HomeTokens.textDim,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- rows

@Composable
private fun AddTaskField(
    input: String,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "+",
            fontFamily = TerminalTheme.mono,
            fontSize = 14.sp,
            color = HomeTokens.accent,
        )
        BasicTextField(
            value = input,
            onValueChange = onInput,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TerminalTheme.mono,
                fontSize = 13.sp,
                color = HomeTokens.textPrimary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            cursorBrush = SolidColor(HomeTokens.accent),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 7.dp, bottom = 7.dp),
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) {
                        Text(
                            text = "Add a task…",
                            fontFamily = TerminalTheme.mono,
                            fontSize = 13.sp,
                            color = HomeTokens.textDim,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun TodoRow(
    task: TodoTask,
    section: TodoSection,
    onToggleDone: () -> Unit,
    onToggleStar: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Check(done = task.done, onToggle = onToggleDone)
        Text(
            text = task.text,
            fontFamily = TerminalTheme.mono,
            fontSize = 12.sp,
            color = if (task.done || task.archived) HomeTokens.textDim else HomeTokens.textPrimary,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        when (section) {
            TodoSection.TODAY -> Star(starred = task.starred, onToggle = onToggleStar)
            TodoSection.DONE -> RowAction("ARCHIVE", onClick = onArchive)
            TodoSection.ARCHIVED -> RowAction("RESTORE", onClick = onRestore)
        }
    }
}

/** The completion box: square, terminal-drawn, no Material checkbox. */
@Composable
private fun Check(done: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(16.dp)
            .then(if (done) Modifier.background(HomeTokens.accent, shape) else Modifier)
            .border(1.dp, if (done) HomeTokens.accent else HomeTokens.textDim, shape)
            .clickable(
                role = Role.Checkbox,
                onClickLabel = if (done) "Mark not done" else "Mark done",
            ) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Text(
                text = "✓",
                fontFamily = TerminalTheme.mono,
                fontSize = 10.sp,
                color = HomeTokens.onAccent,
            )
        }
    }
}

@Composable
private fun Star(starred: Boolean, onToggle: () -> Unit) {
    Text(
        text = if (starred) "★" else "☆",
        fontFamily = TerminalTheme.mono,
        fontSize = 14.sp,
        color = if (starred) HomeTokens.accentBright else HomeTokens.textDim,
        modifier = Modifier
            .clickable(
                role = Role.Button,
                onClickLabel = if (starred) "Remove star" else "Star (priority)",
            ) { onToggle() }
            .padding(4.dp),
    )
}

@Composable
private fun RowAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = TerminalTheme.mono,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = HomeTokens.textDim,
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}
