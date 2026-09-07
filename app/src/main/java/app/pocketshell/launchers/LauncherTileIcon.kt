package app.pocketshell.launchers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.home.MonogramTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M7.1 P1/P2 — the shared launcher tile (PART D/E): the ONE icon path for
 * the Home grids and the management screen. Resolution order:
 *
 *   1. the user's IMPORTED icon copy (decodes from app storage) — an
 *      explicit user choice outranks everything;
 *   2. the BUNDLED curated icon (P2, packaged in the APK) for curated ids;
 *   3. the deterministic text badge on the established monogram plate.
 *
 * Every failure path — missing file, deleted import source, invalid bytes,
 * missing/unreadable asset — degrades to the badge, never to a broken
 * image. Offline by construction: both sources are local.
 */
@Composable
fun LauncherTileIcon(
    launcherId: String,
    iconFile: String?,
    badge: String,
    size: Dp,
) {
    val context = LocalContext.current
    val bitmap = produceState<Bitmap?>(initialValue = null, launcherId, iconFile) {
        value = withContext(Dispatchers.IO) {
            loadUserIcon(context, iconFile) ?: loadBundledIcon(context, launcherId)
        }
    }
    val image = bitmap.value?.asImageBitmap()
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(HomeTokens.appTileRadius)),
            contentScale = ContentScale.Crop,
        )
    } else {
        MonogramTile(monogram = badge, size = size)
    }
}

/** The imported copy from app storage, or null (P1 contract, unchanged). */
private fun loadUserIcon(context: Context, iconFile: String?): Bitmap? {
    if (iconFile == null) return null
    val file = LauncherIconStore.file(context.filesDir, iconFile) ?: return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 256)
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()
}

/** The packaged curated icon for [launcherId], or null when absent/unreadable. */
private fun loadBundledIcon(context: Context, launcherId: String): Bitmap? {
    val path = LauncherBundledIcons.assetPathFor(launcherId) ?: return null
    return runCatching {
        context.assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}
