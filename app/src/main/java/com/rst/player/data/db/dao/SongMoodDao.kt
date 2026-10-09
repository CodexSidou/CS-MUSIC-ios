package com.rst.player.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rst.player.data.db.entity.SongMoodEntity

@Dao
interface SongMoodDao {
    @Query("SELECT * FROM song_moods WHERE songId IN (:ids)")
    suspend fun getFor(ids: List<String>): List<SongMoodEntity>

    @Query("SELECT * FROM song_moods WHERE songId = :id")
    suspend fun get(id: String): SongMoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SongMoodEntity)
}
