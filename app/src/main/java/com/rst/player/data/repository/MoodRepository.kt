package com.rst.player.data.repository

import android.content.Context
import com.rst.player.data.audio.AudioMoodAnalyzer
import com.rst.player.data.db.dao.SongMoodDao
import com.rst.player.data.db.entity.SongMoodEntity
import com.rst.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache + lazy on-device audio analysis of songs. Analysis is done a few tracks
 * at a time in the background so the first Home loads stay fast while the
 * library's mood fingerprints slowly fill up.
 */
class MoodRepository(
    private val context: Context,
    private val dao: SongMoodDao
) {

    suspend fun moodsFor(songs: List<Song>): Map<String, SongMoodEntity> {
        if (songs.isEmpty()) return emptyMap()
        return dao.getFor(songs.map { it.id.toString() }).associateBy { it.songId }
    }

    /** Analyzes up to [maxNew] un-cached songs from the pool and returns the
     *  merged mood map for the whole pool. */
    suspend fun analyzeBatch(songs: List<Song>, maxNew: Int = 6): Map<String, SongMoodEntity> {
        if (songs.isEmpty()) return emptyMap()
        val ids = songs.map { it.id.toString() }.distinct()
        val existing = dao.getFor(ids).associateBy { it.songId }
        val result = existing.toMutableMap()
        val missing = songs
            .distinctBy { it.id }
            .filter { it.id.toString() !in existing }
            .take(maxNew)
        for (song in missing) {
            val mood = withContext(Dispatchers.IO) {
                runCatching { AudioMoodAnalyzer.analyze(context, song) }.getOrNull()
            } ?: continue
            runCatching { dao.upsert(mood) }
            result[song.id.toString()] = mood
        }
        return result
    }
}
