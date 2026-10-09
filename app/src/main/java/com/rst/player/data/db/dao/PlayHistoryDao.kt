package com.rst.player.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rst.player.data.db.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    @Query("SELECT * FROM play_history WHERE songId = :songId")
    suspend fun get(songId: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY count DESC, playedAt DESC LIMIT :limit")
    suspend fun mostPlayed(limit: Int): List<PlayHistoryEntity>

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PlayHistoryEntity>

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM play_history")
    suspend fun size(): Int
}
