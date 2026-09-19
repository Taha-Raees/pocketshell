package app.pocketshell.widget.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.HomeAppContext
import app.pocketshell.widget.HomeApplication
import app.pocketshell.widget.HomeAppSpec
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * M8.4 — NOTES: the quick-capture Home Application. The card IS the app
 * (the reference ServersApp pattern):
 *
 *   list ("N notes" + search + rows — pinned ★ first, newest touched first)
 *     ↓ tap a row / + NEW
 *   editor (optional title + monospace body; going back COMMITS the draft)
 *     ↓ back (the card's OWN back handler — only from the list does back
 *       reach the rest of Home)
 *
 * v1 scope is quick capture, deliberately NOT an editor suite:
 *   - NO markdown rendering. The body is plain text in TerminalTheme.mono
 *     — for snippets the monospace layout IS the fidelity; parsing user
 *     prose into markup inside a 172-208dp card adds render bugs and
 *     (worse) a difference between what was typed and what is stored.
 *   - Persistence is the application's OWN notes_store DataStore
 *     (NotesRepository): the list survives process death, rotation and
 *     carousel swipes; only selection/draft/search state is
 *     rememberSaveable. No cloud, no accounts.
 *   - No polling anywhere: writes happen exactly when the user commits.
 *   - A blank draft is never stored, and backing out never destroys an
 *     existing note — DELETE is the only destructive action.
 *
 * Every visual token comes from HomeTokens → TerminalTheme; the card
 * follows the user's selected theme like every Home application.
 */
object NotesApp : HomeApplication() {

    const val ID = "notes"

    override val spec = HomeAppSpec(
        id = ID,
        name = "Notes",
        summary = "Quick capture — commands, reminders, ideas, snippets, stored on this device",
    )

    @Composable
    override fun Content(context: HomeAppContext) {
        val appContext = LocalContext.current.applicationContext
        val store = remember { NotesRepository(appContext) }
        val scope = rememberCoroutineScope()
        // The notes are the store's truth, collected lifecycle-aware; the
        // list recomposition is keyed rows in a lazy column — cheap.
        val notes by store.notes.collectAsStateWithLifecycle(initialValue = emptyList())

        // The application's own navigation + draft state, all saveable →
        // rotation and Home↔Settings round-trips restore them (the same
        // discipline as the ServersApp detail selection).
        var editorOpen by rememberSaveable { mutableStateOf(false) }
        var editorId by rememberSaveable { mutableStateOf("") } // "" = a new draft
        var draftTitle by rememberSaveable { mutableStateOf("") }
        var draftBody by rememberSaveable { mutableStateOf("") }
        var draftPinned by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }

        val editorNote = editorId.takeIf { it.isNotEmpty() }
            ?.let { id -> notes.firstOrNull { it.id == id } }

        fun commitDraft() {
            val title = draftTitle.trim().take(NoteOps.MAX_TITLE)
            val body = draftBody.take(NoteOps.MAX_BODY)
            if (title.isBlank() && body.isBlank()) return // never an empty note
            scope.launch {
                val now = System.currentTimeMillis()
                val base = editorNote
                    ?: StickyNote(id = UUID.randomUUID().toString(), createdAtMs = now)
                store.save(
                    NoteOps.upsert(
                        notes,
                        base.copy(
                            title = title,
                            body = body,
                            pinned = draftPinned,
                            updatedAtMs = now,
                        ),
                    ),
                )
            }
        }

        val commitAndClose = {
            commitDraft()
            editorOpen = false
        }

        // A selection whose note vanished degrades to the list — never a
        // stale editor; back goes list→Home only from the list.
        BackHandler(enabled = editorOpen && (editorId.isEmpty() || editorNote != null)) {
            commitAndClose()
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = NotesLayout.from(maxWidth.value, maxHeight.value)
            if (editorOpen && (editorId.isEmpty() || editorNote != null)) {
                NotesEditor(
                    title = draftTitle,
                    body = draftBody,
                    pinned = draftPinned,
                    canDelete = editorNote != null,
                    onTitle = { draftTitle = it },
                    onBody = { draftBody = it },
                    onTogglePin = {
                        draftPinned = !draftPinned
                        commitDraft()
                    },
                    onDelete = {
                        val id = editorId
                        editorOpen = false
                        if (id.isNotEmpty()) {
                            scope.launch { store.save(NoteOps.remove(notes, id)) }
                        }
                    },
                    onBack = commitAndClose,
                )
            } else {
                NotesList(
                    notes = notes,
                    query = query,
                    layout = layout,
                    onQuery = { query = it },
                    onNew = {
                        draftTitle = ""
                        draftBody = ""
                        draftPinned = false
                        editorId = ""
                        editorOpen = true
                    },
                    onOpen = { note ->
                        draftTitle = note.title
                        draftBody = note.body
                        draftPinned = note.pinned
                        editorId = note.id
                        editorOpen = true
                    },
                )
            }
        }
    }
}

/**
 * The responsive contract (the M8.3 shape, the same inner-dp thresholds
 * as ServersLayout/GitLayout/SshLayout): COMPACT keeps single-line rows
 * and drops the footer; ROOMY adds body preview sublines and footer
 * statistics. The list itself is lazy at every size — scrolling never
 * hides the state line, so no row cap exists.
 */
internal enum class NotesLayout(
    val showsPreview: Boolean,
    val showsFooter: Boolean,
) {
    COMPACT(showsPreview = false, showsFooter = false),
    ROOMY(showsPreview = true, showsFooter = true);

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f

        fun from(widthDp: Float, heightDp: Float): NotesLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

// ----------------------------------------------------------------- list

@Composable
private fun NotesList(
    notes: List<StickyNote>,
    query: String,
    layout: NotesLayout,
    onQuery: (String) -> Unit,
    onNew: () -> Unit,
    onOpen: (StickyNote) -> Unit,
) {
    val visible = remember(notes, query) {
        NoteOps.filtered(NoteOps.sortedForDisplay(notes), query)
    }
    val pinnedCount = remember(notes) { notes.count { it.pinned } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header — the application's title bar + the one creation action.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (notes.isNotEmpty()) {
                Text(
                    text = "${notes.size} notes",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = onNew, modifier = Modifier.height(34.dp)) {
                Text("+ NEW", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        }

        // Search — filter-as-you-type, only when there is something to filter.
        if (notes.isNotEmpty()) {
            NoteSearchField(query = query, onQuery = onQuery)
            Spacer(Modifier.height(4.dp))
        }

        // Rows — pinned first (the display sort is the store's order policy),
        // keyed by id so pin/reorder/edit never re-compose the wrong row.
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(
                visible,
                key = { _, note -> note.id },
                contentType = { _, _ -> "note" },
            ) { index, note ->
                NoteRow(
                    note = note,
                    showsPreview = layout.showsPreview,
                    onOpen = { onOpen(note) },
                )
                if (index != visible.lastIndex) {
                    HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))
                }
            }
        }

        // Footer statistics — roomy cards only.
        if (layout.showsFooter && notes.isNotEmpty()) {
            Text(
                text = "${notes.size} NOTES · $pinnedCount PINNED",
                fontFamily = TerminalTheme.mono,
                fontSize = 10.sp,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
        // The honest state line, every density, every theme.
        val stateLine = when {
            notes.isEmpty() -> "Nothing captured"
            visible.isEmpty() -> "No note matches \"$query\""
            else -> "Local notes — stored on this device"
        }
        Text(
            text = stateLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (visible.isNotEmpty()) HomeTokens.accent else HomeTokens.textDim,
            maxLines = 1,
        )
        if (notes.isEmpty()) {
            Text(
                text = "Jot a command, a reminder, an idea — it stays in this card.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun NoteRow(note: StickyNote, showsPreview: Boolean, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Edit note") { onOpen() }
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) {
                Text(
                    text = "★",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 12.sp,
                    color = HomeTokens.accent,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = note.title.ifBlank {
                    NoteOps.previewLine(note.body).ifBlank { "(untitled)" }
                },
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = HomeTokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showsPreview) {
            val preview = NoteOps.previewLine(note.body)
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp, top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteSearchField(query: String, onQuery: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = TerminalTheme.mono,
            fontSize = 12.sp,
            color = HomeTokens.textPrimary,
        ),
        cursorBrush = SolidColor(HomeTokens.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Search,
        ),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HomeTokens.chipRadius))
                    .background(HomeTokens.surfaceRaised)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search notes…",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 12.sp,
                        color = HomeTokens.textDim,
                    )
                }
                inner()
            }
        },
    )
}

// --------------------------------------------------------------- editor

@Composable
private fun NotesEditor(
    title: String,
    body: String,
    pinned: Boolean,
    canDelete: Boolean,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // In-card back header: the ONLY back is the application's own.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Back to Notes") { onBack() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "←",
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                color = HomeTokens.accent,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Notes",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = HomeTokens.textDim,
            )
        }

        // Title — optional, one line.
        EditorField(
            value = title,
            onValueChange = onTitle,
            placeholder = "Title (optional)",
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        // Body — the note itself, monospace: the snippet's real shape.
        EditorField(
            value = body,
            onValueChange = onBody,
            placeholder = "A command, a reminder, a snippet…",
            singleLine = false,
            textStyle = TextStyle(
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = HomeTokens.textPrimary,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = onTogglePin, modifier = Modifier.height(30.dp)) {
                Text(
                    text = if (pinned) "UNPIN" else "PIN",
                    fontFamily = TerminalTheme.mono,
                    color = HomeTokens.accent,
                )
            }
            Spacer(Modifier.weight(1f))
            if (canDelete) {
                TextButton(onClick = onDelete, modifier = Modifier.height(30.dp)) {
                    Text(
                        text = "DELETE",
                        fontFamily = TerminalTheme.mono,
                        color = HomeTokens.danger,
                    )
                }
            }
        }
        Text(
            text = "Kept when you go back — pinned notes sort first.",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(HomeTokens.accent),
        // A multi-line field takes no explicit IME action — the newline
        // must stay a newline (ImeAction.NewLine is not a requestable
        // action in current Compose).
        keyboardOptions = if (singleLine) {
            KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next)
        } else {
            KeyboardOptions(keyboardType = KeyboardType.Ascii)
        },
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = HomeTokens.textDim),
                        maxLines = if (singleLine) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}
