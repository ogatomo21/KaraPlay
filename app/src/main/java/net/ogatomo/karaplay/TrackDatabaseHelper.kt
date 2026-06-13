package net.ogatomo.karaplay

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

class TrackDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tracks.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "tracks"

        private const val COL_URI = "uri"
        private const val COL_LAST_MODIFIED = "last_modified"
        private const val COL_TITLE = "title"
        private const val COL_ARTIST = "artist"
        private const val COL_SOURCE = "source"
        private const val COL_ARTWORK = "artwork_path"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COL_URI TEXT PRIMARY KEY,
                $COL_LAST_MODIFIED INTEGER,
                $COL_TITLE TEXT,
                $COL_ARTIST TEXT,
                $COL_SOURCE TEXT,
                $COL_ARTWORK TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getCachedTracks(source: String? = null): List<Pair<Long, Track>> {
        val tracks = mutableListOf<Pair<Long, Track>>()
        val db = this.readableDatabase
        val cursor = if (source == null) {
            db.query(TABLE_NAME, null, null, null, null, null, null)
        } else {
            db.query(TABLE_NAME, null, "$COL_SOURCE = ?", arrayOf(source), null, null, null)
        }

        if (cursor.moveToFirst()) {
            do {
                val uriStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_URI))
                val lastMod = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_MODIFIED))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE))
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(COL_ARTIST))
                val trackSource = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE))
                val artwork = cursor.getString(cursor.getColumnIndexOrThrow(COL_ARTWORK))?.takeIf { it.isNotEmpty() }

                val track = Track(title, artist, Uri.parse(uriStr), trackSource, artwork)
                tracks.add(lastMod to track)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return tracks
    }

    fun insertOrUpdateTrack(uriStr: String, lastModified: Long, track: Track) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_URI, uriStr)
            put(COL_LAST_MODIFIED, lastModified)
            put(COL_TITLE, track.title)
            put(COL_ARTIST, track.artist)
            put(COL_SOURCE, track.source)
            put(COL_ARTWORK, track.artworkPath ?: "")
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteTrack(uriStr: String) {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, "$COL_URI = ?", arrayOf(uriStr))
    }
}
