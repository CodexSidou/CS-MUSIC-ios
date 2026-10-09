package com.rst.player.data.repository

import com.rst.player.data.db.dao.PlayHistoryDao
import com.rst.player.data.db.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: PlayHistoryDao) {

    suspend fun recordPlay(songId: String, title: String, artist: String) {
        val existing = dao.get(songId)
        dao.upsert(
            PlayHistoryEntity(
                songId = songId,
                title = title,
                artist = artist,
                playedAt = System.currentTimeMillis(),
                count = (existing?.count ?: 0) + 1
            )
        )
    }

    fun observeRecent(limit: Int = 50): Flow<List<PlayHistoryEntity>> =
        dao.observeRecent(limit)

    suspend fun mostPlayed(limit: Int = 100): List<PlayHistoryEntity> = dao.mostPlayed(limit)

    suspend fun recent(limit: Int = 100): List<PlayHistoryEntity> = dao.recent(limit)

    suspend fun clear() = dao.clear()

    suspend fun size(): Int = dao.size()
}
