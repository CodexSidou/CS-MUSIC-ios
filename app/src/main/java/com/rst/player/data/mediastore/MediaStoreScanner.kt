package com.rst.player.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.rst.player.data.model.Song

object MediaStoreScanner {

    fun scan(context: Context): List<Song> {
        return try {
            scanInternal(context)
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreScanner", "scan failed", e)
            emptyList()
        }
    }

    private fun scanInternal(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // Most recently added (i.e. just-downloaded songs) float to the top.
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val duration = cursor.getLong(durCol)
                if (duration <= 0) continue
                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown Title",
                        artist = cursor.getString(artistCol) ?: Song.UNKNOWN_ARTIST,
                        album = cursor.getString(albumCol) ?: Song.UNKNOWN_ALBUM,
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = duration,
                        dataPath = cursor.getString(dataCol) ?: "",
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        year = cursor.getInt(yearCol).takeIf { it > 0 },
                        trackNumber = cursor.getInt(trackCol).takeIf { it > 0 }
                    )
                )
            }
        }
        return songs
    }

    fun albumArtUri(albumId: Long): Uri? =
        if (albumId <= 0) null
        else ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    /**
     * Best-effort artist picture: album art of the first album owned by the
     * artist. The dedicated "artistalbumart" provider is unreliable on modern
     * Android, so we reuse the album-art provider that already works.
     */
    fun artistArtUri(context: Context, artist: String): Uri? {
        return try {
            if (artist.isBlank() || artist == Song.UNKNOWN_ARTIST) null
            else firstAlbumArtForArtist(context, artist)
        } catch (e: Exception) {
            null
        }
    }

    private fun firstAlbumArtForArtist(context: Context, artist: String): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        val selection = "${MediaStore.Audio.Media.ARTIST} = ? AND ${MediaStore.Audio.Media.ALBUM_ID} > 0"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(artist),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val albumId = cursor.getLong(0)
                if (albumId > 0) return albumArtUri(albumId)
            }
        }
        return null
    }
}
