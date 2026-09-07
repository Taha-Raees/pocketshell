package app.pocketshell.launchers

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
 * M7.1 P1 — the shared launcher tile (PART D): the imported icon when one
 * exists and decodes, else the deterministic text badge on the established
 * monogram plate. Every failure path — missing file, deleted import source,
 * invalid bytes — degrades to the badge, never to a broken image. Used by
 * the Home grids and the management screen alike (ONE icon path).
 */
@Composable
fun LauncherTileIcon(iconFile: String?, badge: String, size: Dp) {
    if (iconFile == null) {
        MonogramTile(monogram = badge, size = size)
        return
    }
    val context = LocalContext.current
    val bitmap = produceState<Bitmap?>(initialValue = null, iconFile) {
        value = withContext(Dispatchers.IO) {
            val file = LauncherIconStore.file(context.filesDir, iconFile)
                ?: return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 256)
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
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
