package app.pocketshell.ui.gui

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pocketshell.gui.GuiGuestContract
import app.pocketshell.gui.GuiRuntimeManager

/**
 * The embedded Linux GUI surface (P3): one kiosk app rendered by the
 * in-process mc compositor onto a SurfaceView, input fed back over the
 * runtime command queue.
 *
 * Lifecycle contract (all transitions real events):
 *   surfaceCreated  → attach
 *   surfaceChanged  → re-attach (resize)
 *   surfaceDestroyed→ detach
 *   onDispose       → stop guest client + compositor (deterministic teardown)
 */
@Composable
fun GuiScreen(
    manager: GuiRuntimeManager,
    statusText: String,
    onStartRuntime: () -> Unit,
    onStopRuntime: () -> Unit,
    onLaunchClient: () -> Unit,
    onKillClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onStartRuntime) { Text("Start runtime") }
            TextButton(onClick = onLaunchClient) { Text("Launch client") }
            TextButton(onClick = onKillClient) { Text("Kill client") }
            TextButton(onClick = onStopRuntime) { Text("Stop") }
        }
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val s = holder.surface
                            if (s != null && s.isValid) {
                                manager.attach(s, holder.surfaceFrame.width(), holder.surfaceFrame.height())
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            val s = holder.surface
                            if (s != null && s.isValid) {
                                manager.attach(s, width, height)
                            }
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            manager.detach()
                        }
                    })
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnTouchListener { v, event ->
                        v.requestFocus()
                        handleTouch(manager, event, v.width, v.height)
                        true
                    }
                    setOnKeyListener { _, keyCode, event ->
                        handleKey(manager, keyCode, event)
                    }
                }
            },
        )
    }
}

/** Touch → compositor pointer + touch (v1: pointer-emulation primary). */
private fun handleTouch(manager: GuiRuntimeManager, event: MotionEvent, vw: Int, vh: Int) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            manager.pointerButton(BTN_LEFT, true)
            manager.pointerMotion(event.x.toInt(), event.y.toInt())
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            manager.touch(0, event.getPointerId(event.actionIndex), event.x.toInt(), event.y.toInt())
        }
        MotionEvent.ACTION_MOVE -> {
            manager.pointerMotion(event.x.toInt(), event.y.toInt())
        }
        MotionEvent.ACTION_UP -> {
            manager.pointerMotion(event.x.toInt(), event.y.toInt())
            manager.pointerButton(BTN_LEFT, false)
        }
        MotionEvent.ACTION_POINTER_UP -> {
            manager.touch(2, event.getPointerId(event.actionIndex), event.x.toInt(), event.y.toInt())
        }
        MotionEvent.ACTION_CANCEL -> manager.touch(2, 0, event.x.toInt(), event.y.toInt())
    }
}

private const val BTN_LEFT = 0x110

/** Hardware + deck keys → evdev codes; unhandled keys report false. */
private fun handleKey(manager: GuiRuntimeManager, keyCode: Int, event: KeyEvent): Boolean {
    val down = event.action == KeyEvent.ACTION_DOWN
    if (!down && event.action != KeyEvent.ACTION_UP) return false
    if (event.repeatCount > 0 && !down) return false
    val code = event.scanCode.takeIf { it != 0 }
        ?: GuiGuestContract.keyCodeToEvdev(keyCode)
        ?: return false
    manager.key(code, down)
    return true
}
