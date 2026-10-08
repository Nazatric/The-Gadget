package com.nazatric.thegadget.data.games

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nazatric.thegadget.data.db.GameEntity
import com.nazatric.thegadget.data.db.GadgetDatabase
import com.nazatric.thegadget.logic.parseGameManifest
import com.nazatric.thegadget.logic.safeZipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

class GameRepository(
    private val context: Context,
    private val db: GadgetDatabase,
) {
    val games = db.games().games()
    private val root = File(context.filesDir, "games").apply { mkdirs() }

    fun gamesRoot(): File = root

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val folders = root.listFiles()?.filter { it.isDirectory && File(it, "index.html").exists() }.orEmpty()
        val seen = folders.map { it.name }.toSet()
        val known = db.games().ids().toSet()
        (known - seen).forEach { db.games().delete(it) }
        folders.forEach { folder ->
            val manifestFile = File(folder, "manifest.json")
            val manifest = parseGameManifest(
                if (manifestFile.exists()) manifestFile.readText() else null,
                folder.name,
            )
            val art = listOfNotNull(manifest.artwork, "cover.png", "cover.jpg", "artwork.png", "icon.png")
                .map { File(folder, it) }
                .firstOrNull { it.exists() }
            val entry = File(folder, manifest.entry)
            db.games().upsert(
                GameEntity(
                    id = folder.name,
                    title = manifest.title,
                    description = manifest.description,
                    artworkPath = art?.absolutePath,
                    entryPath = if (entry.exists()) entry.absolutePath else File(folder, "index.html").absolutePath,
                    rootPath = folder.absolutePath,
                    author = manifest.author,
                    version = manifest.version,
                    addedAt = folder.lastModified(),
                ),
            )
        }
    }

    suspend fun importZip(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(context.cacheDir, "game-import-${System.currentTimeMillis()}").apply { mkdirs() }
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "could not open the zip" }
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var wrote = 0
                    while (entry != null) {
                        val safe = safeZipEntry(entry.name)
                        if (safe != null && !entry.isDirectory) {
                            val dest = File(temp, safe)
                            require(dest.canonicalPath.startsWith(temp.canonicalPath)) { "blocked unsafe zip entry" }
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { out -> zip.copyTo(out) }
                            wrote++
                            if (wrote > 4000) error("zip has too many files")
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val html = findIndex(temp) ?: error("zip does not contain index.html")
            val sourceRoot = html.parentFile ?: temp
            val id = uniqueId(sourceRoot.name.ifBlank { "game" })
            val dest = File(root, id)
            sourceRoot.copyRecursively(dest, overwrite = true)
            temp.deleteRecursively()
            indexFolder(dest)
            dest.name
        }
    }

    suspend fun importTree(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = DocumentFile.fromTreeUri(context, uri) ?: error("folder is not readable")
            val id = uniqueId(doc.name ?: "game")
            val dest = File(root, id)
            dest.mkdirs()
            copyTree(doc, dest, 0)
            if (findIndex(dest) == null) {
                dest.deleteRecursively()
                error("folder does not contain index.html")
            }
            indexFolder(dest)
            id
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(root, id).deleteRecursively()
        db.games().delete(id)
    }

    private suspend fun indexFolder(folder: File) {
        val manifestFile = File(folder, "manifest.json")
        val manifest = parseGameManifest(
            if (manifestFile.exists()) manifestFile.readText() else null,
            folder.name,
        )
        val art = listOfNotNull(manifest.artwork, "cover.png", "cover.jpg", "artwork.png")
            .map { File(folder, it) }
            .firstOrNull { it.exists() }
        db.games().upsert(
            GameEntity(
                id = folder.name,
                title = manifest.title,
                description = manifest.description,
                artworkPath = art?.absolutePath,
                entryPath = File(folder, manifest.entry).takeIf { it.exists() }?.absolutePath
                    ?: File(folder, "index.html").absolutePath,
                rootPath = folder.absolutePath,
                author = manifest.author,
                version = manifest.version,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun copyTree(doc: DocumentFile, dest: File, depth: Int) {
        if (depth > 8) return
        if (doc.isDirectory) {
            doc.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                if (name == "." || name == "..") return@forEach
                val next = File(dest, name)
                if (child.isDirectory) {
                    next.mkdirs()
                    copyTree(child, next, depth + 1)
                } else {
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        next.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun findIndex(dir: File): File? {
        if (File(dir, "index.html").exists()) return File(dir, "index.html")
        return dir.walkTopDown().maxDepth(3).firstOrNull { it.isFile && it.name.equals("index.html", true) }
    }

    private fun uniqueId(raw: String): String {
        val base = raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "game" }.take(40)
        var id = base
        var n = 2
        while (File(root, id).exists()) {
            id = "$base-$n"
            n++
        }
        return id
    }
}
