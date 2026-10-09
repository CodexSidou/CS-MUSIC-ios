package com.rst.player

import android.content.Context
import com.rst.player.data.db.RstDatabase
import com.rst.player.data.preferences.UserPreferences
import com.rst.player.data.repository.HistoryRepository
import com.rst.player.data.repository.ModeRepository
import com.rst.player.data.repository.MoodRepository
import com.rst.player.data.repository.MusicRepository
import com.rst.player.data.repository.PlaylistRepository
import com.rst.player.player.PlayerController
import com.rst.player.recommender.RecommendationEngine
import com.rst.player.youtube.YouTubeRepository

class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val database: RstDatabase = RstDatabase.get(appContext)

    val userPreferences = UserPreferences(appContext)

    val musicRepository = MusicRepository(appContext)

    val playlistRepository = PlaylistRepository(
        playlistDao = database.playlistDao(),
        playlistSongDao = database.playlistSongDao()
    )

    val modeRepository = ModeRepository(appContext)

    val historyRepository = HistoryRepository(database.playHistoryDao())

    val moodRepository = MoodRepository(
        context = appContext,
        dao = database.songMoodDao()
    )

    val recommendationEngine = RecommendationEngine(
        songsProvider = { musicRepository.songs.value },
        historyRepository = historyRepository
    )

    val playerController = PlayerController(appContext)

    val youTubeRepository = YouTubeRepository(appContext)
}
