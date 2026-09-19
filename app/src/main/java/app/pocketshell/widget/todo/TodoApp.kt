package app.pocketshell.widget.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import app.pocketshell.widget.IconAction
import kotlinx.coroutines.launch

/**
 * M8.4 — TODO: a PocketShell Home Application answering, in one card,
 * "what do I need to do today?" with zero friction:
 *
 *   type into the inline field + enter → the task exists (the shared
 *   PocketShell keyboard deck is the input; no dialog, no screen).
 *   tap the box → done.  ☆ → starred (sorts first).  tap the TEXT → edit
 *   in place (M8.4.3: the label becomes a field; Done saves; an emptied
 *   label deletes; the "!" control cycles HIGH → NORMAL → LOW, and a HIGH
 *   task wears the accent "!").  archive → history.  trash → gone for
 *   good (M8.4.2: the explicit delete the archive was never allowed to
 *   replace).
 *
 * M8.4.3 — LISTS: one store, several named lists. A chip row under the
 * header picks the list (the Git card's repo-chip pattern); the whole
 * card — tabs, counts, rows — shows the selected list only. The "+"
 * chip opens an inline form (never a dialog); tapping the ACTIVE chip
 * opens the same form for renaming, and only THERE carries the trash —
 * deleting a list deletes its tasks too, behind an explicit confirm.
 * The default list ("My tasks") is never deletable.
 *
 * Design decisions (pinned by the todo test suite):
 *   - LOCAL-FIRST: the store is the app's own "todo_store" DataStore on
 *     the Android side — the card works before the Linux runtime is
 *     ready and needs no guest, no network, no account. It performs no
 *     navigation and polls nothing: reads are a DataStore flow (emits on
 *     change only), writes are DataStore `edit` transactions on its IO
 *     executor.
 *   - The store is a plain JSON array of [TodoTask] under one key plus a
 *     JSON array of [TodoList] under a second key — a future CLI/agent
 *     integration reads and writes the same shape.
 *   - Sections are IN-CARD TABS (Today / Done / Archived), not depth —
 *     there is no detail page, so the card adds no BackHandler (system
 *     back keeps belonging to Home). Counts appear exactly once: on the
 *     tabs, and always for the SELECTED list.
 *   - State split (M8.4.2): the task LIST lives in the DataStore
 *     (survives process death); the active section, draft input,
 *     selected list, task being edited and the inline list form live in
 *     the application's process-scoped [TodoState] holder.
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
        val listsState by repository.lists.collectAsState(initial = null)
        val tasksState by repository.tasks.collectAsState(initial = null)
        // Section, draft text, selected list, task edit and the list form
        // live in the process-scoped holder: they outlive Home's
        // composition, so returning to this card never resets what the
        // user was doing.
        val state = remember { context.stateStore.forApp(TodoApp.ID) { TodoState() } }
        val scope = rememberCoroutineScope()

        // Save the open task edit: blank text deletes, changed text saves,
        // unchanged text is a no-op (no pointless store writes).
        fun commitEditing() {
            val id = state.editingTaskId
            if (id.isEmpty()) return
            val task = tasksState?.firstOrNull { it.id == id } ?: run {
                state.editingTaskId = ""
                state.editingText = ""
                return
            }
            val trimmed = state.editingText.trim()
            when {
                trimmed.isEmpty() -> {
                    state.editingTaskId = ""
                    state.editingText = ""
                    scope.launch { repository.delete(id) }
                }
                trimmed != task.text -> scope.launch { repository.setText(id, trimmed) }
            }
        }

        fun stopEditing() {
            commitEditing()
            state.editingTaskId = ""
            state.editingText = ""
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = TodoLayout.from(maxWidth.value, maxHeight.value)
            TodoContent(
                lists = listsState,
                tasks = tasksState,
                section = state.section,
                input = state.input,
                selectedListId = state.selectedListId,
                editingTaskId = state.editingTaskId,
                editingText = state.editingText,
                listForm = state.listForm,
                layout = layout,
                onSection = { s ->
                    stopEditing()
                    state.section = s
                },
                onInput = { state.input = it },
                onSubmit = {
                    val text = state.input.trim()
                    if (text.isNotEmpty()) {
                        state.input = ""
                        scope.launch { repository.add(text, state.selectedListId) }
                    }
                },
                onSelectList = { id ->
                    stopEditing()
                    state.listForm = null
                    state.selectedListId = id
                },
                onEditList = { list ->
                    stopEditing()
                    state.listForm = ListForm(targetId = list.id, name = list.name)
                },
                onNewList = {
                    stopEditing()
                    state.listForm = ListForm(targetId = null, name = "")
                },
                onListFormName = { name -> state.listForm = state.listForm?.copy(name = name) },
                onListFormSubmit = {
                    state.listForm?.let { form ->
                        if (form.targetId == null) {
                            scope.launch { repository.addList(form.name) }
                        } else {
                            scope.launch { repository.renameList(form.targetId, form.name) }
                        }
                    }
                    state.listForm = null
                },
                onListFormCancel = { state.listForm = null },
                onListFormDeleteRequest = {
                    state.listForm = state.listForm?.copy(confirmingDelete = true)
                },
                onListFormDeleteCancel = {
                    state.listForm = state.listForm?.copy(confirmingDelete = false)
                },
                onListFormDeleteConfirm = {
                    val targetId = state.listForm?.targetId
                    state.listForm = null
                    stopEditing()
                    if (targetId != null) {
                        scope.launch { repository.deleteList(targetId) }
                        if (state.selectedListId == targetId) {
                            state.selectedListId = TodoList.DEFAULT_LIST_ID
                        }
                    }
                },
                onToggleDone = { id -> scope.launch { repository.toggleDone(id) } },
                onToggleStar = { id -> scope.launch { repository.toggleStar(id) } },
                onArchive = { id -> scope.launch { repository.setArchived(id, archived = true) } },
                onRestore = { id -> scope.launch { repository.setArchived(id, archived = false) } },
                onDelete = { id -> scope.launch { repository.delete(id) } },
                onStartEdit = { task ->
                    if (state.editingTaskId != task.id) {
                        stopEditing()
                        state.editingTaskId = task.id
                        state.editingText = task.text
                    }
                },
                onEditingText = { state.editingText = it },
                onCommitEdit = { task ->
                    // Focus loss saves but does not close the editor (Done
                    // closes) — closing here would swallow a same-row tap on
                    // the priority/trash controls.
                    if (state.editingTaskId == task.id) commitEditing()
                },
                onEditDone = { task ->
                    if (state.editingTaskId == task.id) stopEditing()
                },
                onCyclePriority = { id -> scope.launch { repository.cyclePriority(id) } },
            )
        }
    }
}

/**
 * M8.4.2 — the TODO application's process-scoped state: the active tab,
 * the draft input, the selected list, the task being edited in place and
 * the inline list form (null = closed). Owned by the HomeAppStateStore
 * (via the shared ViewModel), so leaving Home and coming back — or
 * swiping pages away and back — lands the user exactly where they were.
 */
internal class TodoState {
    var section by mutableStateOf(TodoSection.TODAY)
    var input by mutableStateOf("")
    var selectedListId by mutableStateOf(TodoList.DEFAULT_LIST_ID)
    var editingTaskId by mutableStateOf("")
    var editingText by mutableStateOf("")
    var listForm by mutableStateOf<ListForm?>(null)
}

/** The inline list form's snapshot: [targetId] null = creating a new list. */
internal data class ListForm(
    val targetId: String?,
    val name: String,
    val confirmingDelete: Boolean = false,
)

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
 * the reference ServersLayout. The rows area scrolls at BOTH densities
 * with no row cap — a row must never be unreachable inside the card.
 * Pure + JVM-tested (TodoLayoutTest).
 */
internal enum class TodoLayout {
    COMPACT,
    ROOMY;

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f

        fun from(widthDp: Float, heightDp: Float): TodoLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

@Composable
private fun TodoContent(
    lists: List<TodoList>?,
    tasks: List<TodoTask>?,
    section: TodoSection,
    input: String,
    selectedListId: String,
    editingTaskId: String,
    editingText: String,
    listForm: ListForm?,
    layout: TodoLayout,
    onSection: (TodoSection) -> Unit,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelectList: (String) -> Unit,
    onEditList: (TodoList) -> Unit,
    onNewList: () -> Unit,
    onListFormName: (String) -> Unit,
    onListFormSubmit: () -> Unit,
    onListFormCancel: () -> Unit,
    onListFormDeleteRequest: () -> Unit,
    onListFormDeleteCancel: () -> Unit,
    onListFormDeleteConfirm: () -> Unit,
    onToggleDone: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStartEdit: (TodoTask) -> Unit,
    onEditingText: (String) -> Unit,
    onCommitEdit: (TodoTask) -> Unit,
    onEditDone: (TodoTask) -> Unit,
    onCyclePriority: (String) -> Unit,
) {
    // One list at a time: scope FIRST, then section — the tabs and their
    // counts describe the selected list only.
    val scoped = tasks?.let { TodoTasks.inList(it, selectedListId) }.orEmpty()
    val today = TodoTasks.today(scoped)
    val done = TodoTasks.done(scoped)
    val archived = TodoTasks.archived(scoped)
    val visible = when (section) {
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
        }

        // The list picker — ONE horizontally-scrollable chip row (the Git
        // card's repo-chip pattern). The active list wears the accent; the
        // "+" chip opens the inline create form. The row always exists
        // once the store has spoken: with one list it is still the only
        // way to reach "+" and (via the active chip) the edit form.
        if (lists != null) {
            Spacer(Modifier.height(4.dp))
            ListChipRow(
                lists = lists,
                selectedId = selectedListId,
                canAdd = lists.size < TodoList.MAX_LISTS,
                onSelect = onSelectList,
                onEdit = onEditList,
                onNew = onNewList,
            )
        }

        // The inline list form — create or rename, never a dialog; the
        // trash lives ONLY here (never on a chip), behind an explicit
        // confirm because deleting a list deletes its tasks.
        if (listForm != null) {
            val target = listForm.targetId
            ListFormCard(
                form = listForm,
                isDefaultTarget = target == TodoList.DEFAULT_LIST_ID,
                taskCount = if (target == null) 0 else tasks?.count { it.listId == target } ?: 0,
                onName = onListFormName,
                onSubmit = onListFormSubmit,
                onCancel = onListFormCancel,
                onDeleteRequest = onListFormDeleteRequest,
                onDeleteCancel = onListFormDeleteCancel,
                onDeleteConfirm = onListFormDeleteConfirm,
            )
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

        // Section tabs — counts are real, per section, always honest —
        // and they are the ONE counter in the card (for the active list).
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

        // Rows — always scrolling, never capped: every task is reachable.
        // fill = false: a short list leaves NO dead middle — the content
        // sits under the tabs and the hint follows it immediately.
        if (visible.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                visible.forEach { task ->
                    if (task.id == editingTaskId) {
                        TodoEditRow(
                            task = task,
                            text = editingText,
                            onText = onEditingText,
                            onCommit = { onCommitEdit(task) },
                            onDone = { onEditDone(task) },
                            onCyclePriority = { onCyclePriority(task.id) },
                            onDelete = { onDelete(task.id) },
                        )
                    } else {
                        TodoRow(
                            task = task,
                            section = section,
                            onToggleDone = { onToggleDone(task.id) },
                            onToggleStar = { onToggleStar(task.id) },
                            onArchive = { onArchive(task.id) },
                            onRestore = { onRestore(task.id) },
                            onDelete = { onDelete(task.id) },
                            onStartEdit = { onStartEdit(task) },
                        )
                    }
                }
            }
        }

        // The one state line — ONLY what the tabs don't already say
        // (the loading dots and the honest empty states). Counts appear
        // exactly once in this card: on the tabs.
        if (tasks == null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "…",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                maxLines = 1,
            )
        } else if (visible.isEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = when (section) {
                    TodoSection.TODAY -> "Add your first task above"
                    TodoSection.DONE -> "Nothing completed yet"
                    TodoSection.ARCHIVED -> "Nothing archived"
                },
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                maxLines = 1,
            )
        }
    }
}

// ----------------------------------------------------------- list chips

/**
 * The list picker chips: active = accent text + accent border, others =
 * dim text + hairline border (the Git repo-chip pattern). Tapping a chip
 * switches the whole card; tapping the ACTIVE chip opens its edit form.
 */
@Composable
private fun ListChipRow(
    lists: List<TodoList>,
    selectedId: String,
    canAdd: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (TodoList) -> Unit,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lists.forEach { list ->
            val active = list.id == selectedId
            Text(
                text = list.name,
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = if (active) HomeTokens.accent else HomeTokens.textDim,
                maxLines = 1,
                modifier = Modifier
                    .border(
                        1.dp,
                        if (active) HomeTokens.accent else HomeTokens.hairline,
                        RoundedCornerShape(HomeTokens.chipRadius),
                    )
                    .clickable(
                        role = Role.Tab,
                        onClickLabel = if (active) "Edit list ${list.name}" else "Switch to list ${list.name}",
                    ) {
                        if (active) onEdit(list) else onSelect(list.id)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        // The "+" chip — hidden at the cap (no affordance that lies).
        if (canAdd) {
            Box(
                modifier = Modifier
                    .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.chipRadius))
                    .clickable(role = Role.Button, onClickLabel = "New list") { onNew() }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New list",
                    tint = HomeTokens.textDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * The inline list form: one field, IME Done = save. Create mode starts
 * blank; edit mode is prefilled and — for every list EXCEPT the default
 * — carries the trash that asks before deleting.
 */
@Composable
private fun ListFormCard(
    form: ListForm,
    isDefaultTarget: Boolean,
    taskCount: Int,
    onName: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    if (form.confirmingDelete && form.targetId != null) {
        // The confirm step: the count is the point — this is the moment
        // the user learns exactly how much one tap removes.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Delete list '${form.name}' and its $taskCount tasks?",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDeleteConfirm, contentPadding = paddingXs()) {
                Text(
                    text = "Delete",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.danger,
                )
            }
            TextButton(onClick = onDeleteCancel, contentPadding = paddingXs()) {
                Text(
                    text = "Cancel",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                )
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (form.targetId == null) {
                Text(
                    text = "+",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 14.sp,
                    color = HomeTokens.accent,
                )
            }
            BasicTextField(
                value = form.name,
                onValueChange = onName,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = TerminalTheme.mono,
                    fontSize = 12.sp,
                    color = HomeTokens.textPrimary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                cursorBrush = SolidColor(HomeTokens.accent),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, top = 5.dp, bottom = 5.dp),
                decorationBox = { inner ->
                    Box {
                        if (form.name.isEmpty()) {
                            Text(
                                text = if (form.targetId == null) "New list name…" else "Rename list…",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 12.sp,
                                color = HomeTokens.textDim,
                            )
                        }
                        inner()
                    }
                },
            )
            // The trash lives ONLY on the edit form, never on a chip — and
            // never on the default list, which is not deletable.
            if (form.targetId != null && !isDefaultTarget) {
                IconAction(
                    icon = Icons.Outlined.Delete,
                    label = "Delete list",
                    tint = HomeTokens.textDim,
                    onClick = onDeleteRequest,
                )
            }
            Text(
                text = "×",
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                color = HomeTokens.textDim,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = "Cancel") { onCancel() }
                    .padding(4.dp),
            )
        }
    }
}

/** TextButton content padding — the Material default is too wide for the card. */
private fun paddingXs() = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

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
    onDelete: () -> Unit,
    onStartEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Check(done = task.done, onToggle = onToggleDone)
        // The priority glyph: a HIGH task wears the accent "!"; NORMAL and
        // LOW wear nothing.
        if (task.priority == TodoTask.PRIORITY_HIGH) {
            Text(
                text = "!",
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = HomeTokens.accent,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        // The text IS the edit affordance: tapping it opens the field.
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
                .clickable(role = Role.Button, onClickLabel = "Edit task") { onStartEdit() }
                .padding(horizontal = 8.dp),
        )
        when (section) {
            TodoSection.TODAY -> Star(starred = task.starred, onToggle = onToggleStar)
            TodoSection.DONE -> IconAction(
                icon = Icons.Outlined.Archive,
                label = "Archive task",
                tint = HomeTokens.textDim,
                onClick = onArchive,
            )
            TodoSection.ARCHIVED -> IconAction(
                icon = Icons.Outlined.Unarchive,
                label = "Restore task",
                tint = HomeTokens.textDim,
                onClick = onRestore,
            )
        }
        IconAction(
            icon = Icons.Outlined.Delete,
            label = "Delete task",
            tint = HomeTokens.textDim,
            onClick = onDelete,
        )
    }
}

/**
 * The row in edit mode (M8.4.3): the label is a prefilled field, IME
 * Done = save; the "!" control cycles HIGH → NORMAL → LOW → HIGH (accent
 * while HIGH); the existing trash stays. Focus loss saves without
 * closing — Done is the close — so a tap on the priority or trash
 * controls can never be swallowed by the row leaving edit mode.
 */
@Composable
private fun TodoEditRow(
    task: TodoTask,
    text: String,
    onText: (String) -> Unit,
    onCommit: () -> Unit,
    onDone: () -> Unit,
    onCyclePriority: () -> Unit,
    onDelete: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(task.id) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = HomeTokens.textPrimary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(HomeTokens.accent),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (!it.isFocused) onCommit() }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Text(
            text = "!",
            fontFamily = TerminalTheme.mono,
            fontSize = 14.sp,
            color = if (task.priority == TodoTask.PRIORITY_HIGH) HomeTokens.accent else HomeTokens.textDim,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "Cycle priority") { onCyclePriority() }
                .padding(4.dp),
        )
        IconAction(
            icon = Icons.Outlined.Delete,
            label = "Delete task",
            tint = HomeTokens.textDim,
            onClick = onDelete,
        )
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
