package com.nazatric.thegadget.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazatric.thegadget.data.db.GameEntity
import com.nazatric.thegadget.games.GameActivity
import com.nazatric.thegadget.ui.components.Hairline
import com.nazatric.thegadget.ui.components.glass
import com.nazatric.thegadget.ui.theme.GadgetColors
import com.nazatric.thegadget.ui.theme.italicLabel
import com.nazatric.thegadget.ui.theme.sans
import java.io.File

@Composable
fun GamesScreen(
    games: List<GameEntity>,
    message: String?,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onImportZip: (Uri) -> Unit,
    onImportTree: (Uri) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportZip(uri)
    }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            onImportTree(uri)
        }
    }
    ScreenFrame("games", glow, grain, onBack) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("an empty shelf", style = italicLabel(28.sp))
            Spacer(Modifier.height(6.dp))
            Text(
                "Nothing is installed. Add an HTML game you own as a zip with index.html, or pick a folder. Games open in an isolated view. The rest of the gadget stays native.",
                style = sans(14.sp, GadgetColors.soft),
            )
            Spacer(Modifier.height(16.dp))
            Text("add zip", style = italicLabel(16.sp), modifier = Modifier.glass(16.dp).clickable { zip.launch(arrayOf("application/zip", "application/octet-stream")) }.padding(14.dp))
            Spacer(Modifier.height(8.dp))
            Text("add folder", style = italicLabel(16.sp), modifier = Modifier.glass(16.dp).clickable { tree.launch(null) }.padding(14.dp))
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(message, style = sans(13.sp, GadgetColors.dim))
            }
            Spacer(Modifier.height(18.dp))
            if (games.isEmpty()) {
                Text("no games yet", style = italicLabel(16.sp, GadgetColors.dim))
            }
            games.forEach { game ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(context, GameActivity::class.java)
                                    .putExtra(GameActivity.EXTRA_ROOT, game.rootPath)
                                    .putExtra(GameActivity.EXTRA_ENTRY, game.entryPath)
                                    .putExtra(GameActivity.EXTRA_TITLE, game.title),
                            )
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val art = game.artworkPath?.let { path ->
                        runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
                    }
                    if (art != null) {
                        Image(art.asImageBitmap(), null, Modifier.size(54.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text("○", style = italicLabel(18.sp), modifier = Modifier.size(54.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(game.title, style = italicLabel(18.sp))
                        Text(game.description.ifBlank { game.author.ifBlank { "local html" } }, style = sans(12.sp, GadgetColors.dim), maxLines = 2)
                    }
                    Text("remove", style = sans(12.sp, GadgetColors.dim), modifier = Modifier.clickable { onDelete(game.id) }.padding(8.dp))
                }
                Hairline()
            }
        }
    }
}
