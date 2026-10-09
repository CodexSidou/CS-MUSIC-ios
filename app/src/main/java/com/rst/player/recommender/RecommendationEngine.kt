package com.rst.player.recommender

import com.rst.player.data.db.entity.SongMoodEntity
import com.rst.player.data.model.Song
import com.rst.player.data.repository.HistoryRepository
import java.util.Calendar

/** Which part of the day the user is in — drives time-aware mixes. */
enum class TimePeriod(val display: String) {
    MORNING("morning"),
    AFTERNOON("afternoon"),
    EVENING("evening"),
    NIGHT("night");

    companion object {
        fun now(): TimePeriod = fromHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

        fun fromHour(hour: Int): TimePeriod = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * Fully local, on-device recommendation engine.
 * Learns from play counts, recency and affinity to build
 * "Made For You", "Because you played" and smart auto-queues.
 */
class RecommendationEngine(
    private val songsProvider: () -> List<Song>,
    private val historyRepository: HistoryRepository
) {

    data class Taste(
        val artistAffinity: Map<String, Double>,
        val albumAffinity: Map<Long, Double>,
        val periodArtistAffinity: Map<TimePeriod, Map<String, Double>>,
        val playedSongIds: Set<String>
    )

    suspend fun buildTaste(): Taste {
        val plays = historyRepository.mostPlayed(400)
        val songIds = plays.map { it.songId }.toSet()
        val recent = historyRepository.recent(50)

        val artistAffinity = HashMap<String, Double>()
        val albumAffinity = HashMap<Long, Double>()
        val periodArtistAffinity = HashMap<TimePeriod, HashMap<String, Double>>()

        fun periodOf(playedAt: Long): TimePeriod {
            val c = Calendar.getInstance().apply { timeInMillis = playedAt }
            return TimePeriod.fromHour(c.get(Calendar.HOUR_OF_DAY))
        }

        fun addPeriod(period: TimePeriod, artist: String, weight: Double) {
            val bucket = periodArtistAffinity.getOrPut(period) { HashMap() }
            bucket[artist] = (bucket[artist] ?: 0.0) + weight
        }

        plays.forEach { play ->
            val song = songsProvider().find { it.id.toString() == play.songId }
            if (song != null) {
                artistAffinity[song.artist] = artistAffinity[song.artist] ?: 0.0
                artistAffinity[song.artist] = artistAffinity[song.artist]!! + play.count.toDouble()
                addPeriod(periodOf(play.playedAt), song.artist, play.count.toDouble())
                if (song.albumId > 0) {
                    albumAffinity[song.albumId] = albumAffinity[song.albumId] ?: 0.0
                    albumAffinity[song.albumId] = albumAffinity[song.albumId]!! + play.count.toDouble()
                }
            }
        }

        // recency boost: songs played recently count double
        recent.forEach { play ->
            val song = songsProvider().find { it.id.toString() == play.songId }
            if (song != null) {
                artistAffinity[song.artist] = (artistAffinity[song.artist] ?: 0.0) + 2.0
                addPeriod(periodOf(play.playedAt), song.artist, 2.0)
            }
        }

        return Taste(
            artistAffinity = artistAffinity,
            albumAffinity = albumAffinity,
            periodArtistAffinity = periodArtistAffinity.mapValues { it.value.toMap() },
            playedSongIds = songIds
        )
    }

    /**
     * Time-aware "Made For You": high-affinity artists, boosted for the current
     * period of day, blended with an on-device audio-mood fit (so "Night drive"
     * actually sounds like night) and mixed so a single artist can never
     * dominate the row.
     */
    suspend fun madeForYou(
        limit: Int,
        excludeIds: Set<String> = emptySet(),
        period: TimePeriod = TimePeriod.now(),
        moods: Map<String, SongMoodEntity> = emptyMap()
    ): List<Song> {
        val taste = buildTaste()
        val all = songsProvider()
        val periodAff = taste.periodArtistAffinity[period] ?: emptyMap()

        val ranked = all
            .filter { it.id.toString() !in excludeIds }
            .sortedWith(
                compareByDescending<Song> {
                    val fit = moods[it.id.toString()]?.let { m -> MoodClassifier.fitFor(period, m) } ?: 0.0
                    (taste.artistAffinity[it.artist] ?: 0.0) +
                        (periodAff[it.artist] ?: 0.0) * 1.5 +
                        fit * 3.0
                }
                    .thenByDescending { taste.albumAffinity[it.albumId] ?: 0.0 }
            )

        return diversify(ranked, limit, maxPerArtist = 2, taste = taste)
    }

    /**
     * "Discover something new": a mix of genuinely unplayed tracks AND
     * forgotten/low-play tracks from artists you love. The row is
     * diversified across artists so it never floods with one name.
     */
    suspend fun discover(
        limit: Int,
        excludeIds: Set<String> = emptySet(),
        moods: Map<String, SongMoodEntity> = emptyMap()
    ): List<Song> {
        val taste = buildTaste()
        val all = songsProvider().filter { it.id.toString() !in excludeIds }
        if (all.isEmpty()) return emptyList()

        val played = taste.playedSongIds
        val forgotten = all.filter {
            it.id.toString() in played && (taste.artistAffinity[it.artist] ?: 0.0) > 2.0
        }.sortedBy { taste.artistAffinity[it.artist] ?: 0.0 }

        val unplayed = all.filter { it.id.toString() !in played }

        // Interleave: forgotten from loved artists + genuinely new tracks
        val pool = mutableListOf<Song>()
        val fi = forgotten.iterator()
        val ui = unplayed.iterator()
        while (pool.size < limit * 2) {
            if (fi.hasNext()) pool.add(fi.next())
            if (ui.hasNext()) pool.add(ui.next())
            if (!fi.hasNext() && !ui.hasNext()) break
        }

        return diversify(pool, limit, maxPerArtist = 2, taste = taste)
    }

    /**
     * Reorders a ranked list so consecutive cards alternate between artists and
     * no single artist can take more than [maxPerArtist] slots. Affinity-sorted
     * order is preserved per artist (their best tracks come first).
     */
    private fun diversify(
        ranked: List<Song>,
        limit: Int,
        maxPerArtist: Int,
        taste: Taste
    ): List<Song> {
        if (ranked.isEmpty()) return emptyList()
        val byArtist = LinkedHashMap<String, MutableList<Song>>()
        for (song in ranked) byArtist.getOrPut(song.artist) { ArrayList() }.add(song)

        val result = mutableListOf<Song>()
        val seen = HashSet<Long>()
        var depth = 0
        var added = true
        while (result.size < limit && added) {
            added = false
            val artists = byArtist.keys.toList()
            for (artist in artists) {
                if (result.size >= limit) break
                val pool = byArtist[artist] ?: continue
                if (depth < pool.size && seen.add(pool[depth].id)) {
                    result.add(pool[depth])
                    added = true
                }
            }
            depth++
        }
        if (result.size < limit) {
            for (song in ranked) {
                if (result.size >= limit) break
                if (seen.add(song.id)) result.add(song)
            }
        }
        return result.take(limit)
    }

    /** "Because you played X" — similarity to a seed song blended with overall taste. */
    suspend fun becauseYouPlayed(
        seed: Song,
        limit: Int,
        excludeIds: Set<String> = emptySet(),
        moods: Map<String, SongMoodEntity> = emptyMap()
    ): List<Song> {
        val taste = buildTaste()
        val all = songsProvider()
        val scored = all
            .filter { it.id != seed.id && it.id.toString() !in excludeIds }
            .map { it to score(it, seed, taste, moods) }
            .sortedByDescending { it.second }

        return diversify(scored.map { it.first }, limit, maxPerArtist = 2, taste = taste)
    }

    /** Similar songs for the Now Playing screen — diversified across artists,
     *  matching audio mood and taste rather than flooding with the same artist. */
    suspend fun similarTo(
        seed: Song,
        limit: Int = 10,
        moods: Map<String, SongMoodEntity> = emptyMap()
    ): List<Song> =
        becauseYouPlayed(seed, limit, moods = moods)

    /** Smart auto-play continuation — never lets the music stop. */
    suspend fun smartNext(seed: Song, limit: Int = 30, excludeIds: Set<String> = emptySet(), moods: Map<String, SongMoodEntity> = emptyMap()): List<Song> {
        val similar = becauseYouPlayed(seed, limit, excludeIds, moods)
        val fills = madeForYou(limit, excludeIds + similar.map { it.id.toString() }, moods = moods)
        val seen = similar.map { it.id }.toMutableSet()
        val result = similar.toMutableList()
        for (song in fills) {
            if (result.size >= limit) break
            if (song.id !in seen) {
                result.add(song)
                seen.add(song.id)
            }
        }
        return result
    }

    private fun score(candidate: Song, seed: Song, taste: Taste, moods: Map<String, SongMoodEntity>): Double {
        var s = 0.0
        // Same album: strong signal
        if (candidate.albumId == seed.albumId && candidate.albumId > 0) s += 8.0
        // Same artist: moderate signal (reduced from 6 to 3 so we don't flood)
        if (candidate.artist == seed.artist) s += 3.0
        // Cross-artist taste: penalise if too many from the same artist already
        val artistCount = taste.artistAffinity[candidate.artist] ?: 0.0
        s += artistCount * 0.4  // reduced from 0.8
        s += (taste.albumAffinity[candidate.albumId] ?: 0.0) * 0.5
        // Mood similarity: bonus if both have mood data and it matches
        val seedMood = moods[seed.id.toString()]
        val candMood = moods[candidate.id.toString()]
        if (seedMood != null && candMood != null) {
            val bpmDiff = kotlin.math.abs(seedMood.bpm - candMood.bpm) / 130f
            val energyDiff = kotlin.math.abs(seedMood.energy - candMood.energy)
            val brightnessDiff = kotlin.math.abs(seedMood.brightness - candMood.brightness)
            val valenceDiff = kotlin.math.abs(seedMood.valence - candMood.valence)
            s += (1.0 - bpmDiff.coerceIn(0f, 1f).toDouble()) * 2.0
            s += (1.0 - energyDiff.toDouble()) * 1.5
            s += (1.0 - brightnessDiff.toDouble()) * 1.5
            s += (1.0 - valenceDiff.toDouble()) * 1.0
        }
        // Title word similarity
        val seedWords = seed.title.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        if (seedWords.isNotEmpty()) {
            val candWords = candidate.title.lowercase().split(Regex("\\W+")).toSet()
            val overlap = (seedWords intersect candWords).size
            s += overlap * 1.5
        }
        // Recency tie-breaker
        s += taste.playedSongIds.indexOf(candidate.id.toString()) * -0.01
        return s
    }
}
