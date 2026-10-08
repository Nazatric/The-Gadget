package com.nazatric.thegadget.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val uri: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val year: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val comment: String,
    val copyright: String,
    val lyrics: String,
    val encoder: String,
    val bitrate: Int,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val durationMs: Long,
    val mime: String,
    val codec: String,
    val lossless: Boolean,
    val fileSize: Long,
    val relativePath: String,
    val displayName: String,
    val bucket: String,
    val dateModified: Long,
    val dateAdded: Long,
    val replayGainTrack: Float?,
    val replayGainAlbum: Float?,
    val hasArt: Boolean,
    val enriched: Boolean,
)

data class TrackSummary(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val durationMs: Long,
    val year: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val dateModified: Long,
    val dateAdded: Long,
    val relativePath: String,
    val bucket: String,
    val lossless: Boolean,
    val codec: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val bitrate: Int,
    val replayGainTrack: Float?,
    val replayGainAlbum: Float?,
    val hasArt: Boolean,
    val fileSize: Long,
    val mime: String,
    val source: String,
)

data class TrackStamp(
    val uri: String,
    val dateModified: Long,
    val fileSize: Long,
    val enriched: Boolean,
    val source: String,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "uri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("uri")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val uri: String,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val uri: String,
    val addedAt: Long,
)

@Entity(tableName = "history", indices = [Index("playedAt"), Index("uri")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val playedAt: Long,
    val positionMs: Long,
)

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val artworkPath: String?,
    val entryPath: String,
    val rootPath: String,
    val author: String,
    val version: String,
    val addedAt: Long,
)

@Dao
interface TrackDao {
    @Query(
        """
        SELECT uri, title, artist, album, albumArtist, genre, composer, durationMs, year,
               trackNumber, discNumber, dateModified, dateAdded, relativePath, bucket, lossless,
               codec, sampleRate, bitDepth, channels, bitrate, replayGainTrack, replayGainAlbum,
               hasArt, fileSize, mime, source
        FROM tracks
        """,
    )
    fun summaries(): Flow<List<TrackSummary>>

    @Query("SELECT uri, dateModified, fileSize, enriched, source FROM tracks")
    suspend fun stamps(): List<TrackStamp>

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    suspend fun track(uri: String): TrackEntity?

    @Query("SELECT lyrics FROM tracks WHERE uri = :uri")
    suspend fun lyrics(uri: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE uri IN (:uris)")
    suspend fun deleteUris(uris: List<String>)

    @Query("SELECT COUNT(*) FROM tracks")
    fun count(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM tracks")
    fun totalDuration(): Flow<Long>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM tracks")
    suspend fun totalBytes(): Long
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT uri FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    fun uris(id: Long): Flow<List<String>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id AND uri = :uri")
    suspend fun remove(id: Long, uri: String)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :id")
    fun count(id: Long): Flow<Int>
}

@Dao
interface FavoriteDao {
    @Query("SELECT uri FROM favorites")
    fun uris(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun remove(uri: String)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity)

    @Query(
        """
        SELECT uri FROM history
        GROUP BY uri
        ORDER BY MAX(playedAt) DESC
        LIMIT :limit
        """,
    )
    fun recentUris(limit: Int = 40): Flow<List<String>>

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY addedAt DESC")
    fun games(): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM games")
    fun count(): Flow<Int>

    @Query("SELECT id FROM games")
    suspend fun ids(): List<String>
}

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        GameEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GadgetDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun games(): GameDao

    companion object {
        @Volatile private var instance: GadgetDatabase? = null

        fun get(context: Context): GadgetDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GadgetDatabase::class.java,
                    "gadget.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
